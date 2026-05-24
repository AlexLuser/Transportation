package com.fm.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.logistics.config.RabbitMQConfig;
import com.fm.logistics.dto.*;
import com.fm.logistics.entity.*;
import com.fm.logistics.mapper.*;
import com.fm.logistics.mq.LastMileActivateMessage;
import com.fm.logistics.service.*;
import com.fm.logistics.dto.HubSelectionResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 配送批次服务实现
 *
 * Hub-and-Spoke 核心流程：
 *   createBatch() → VRP排序 → 选Hub → 创建干线路线 → 创建N条末端路线（待激活）
 *   activateLastMileRoutes() → 干线到Hub后触发 → 激活末端路线 → 通知driver创建配送单
 */
@Service
public class LogisticsBatchServiceImpl implements LogisticsBatchService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsBatchServiceImpl.class);

    @Autowired
    private LogisticsBatchMapper batchMapper;

    @Autowired
    private LogisticsBatchItemMapper batchItemMapper;

    @Autowired
    private LogisticsHubMapper hubMapper;

    @Autowired
    private DispatchPoolMapper dispatchPoolMapper;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private LogisticsRouteMapper routeMapper;

    @Autowired
    private VrpService vrpService;

    @Autowired
    private LogisticsHubService hubService;

    @Autowired
    private LlmHubSelectionService llmHubSelectionService;

    @Autowired
    private LogisticsRouteService routeService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // ----------------------------------------------------------------
    //  创建批次（核心入口）
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public BatchDetailDTO createBatch(CreateBatchRequestDTO requestDTO) {
        log.info("[Batch] 开始创建批次，订单数={}, useHub={}",
                requestDTO.getOrderIds().size(), requestDTO.getUseHub());

        // ── 1. VRP 规划最优访问顺序 ────────────────────────────────
        VrpResultDTO vrpResult = vrpService.computeOptimalOrder(
                requestDTO.getWarehouseLat(), requestDTO.getWarehouseLng(),
                requestDTO.getOrderIds(),
                extractLats(requestDTO),
                extractLngs(requestDTO),
                extractAddresses(requestDTO),
                extractReceiverNames(requestDTO),
                extractReceiverPhones(requestDTO)
        );

        // ── 2. 智能选 Hub（方案C：LLM综合距离+负载+交通多因素决策） ──
        LogisticsHub hub = null;
        boolean useHub = Boolean.TRUE.equals(requestDTO.getUseHub());
        HubSelectionResultDTO hubSelectionResult = null;

        if (useHub) {
            if (requestDTO.getHubId() != null) {
                // 前端已指定 Hub，直接使用
                hub = hubService.getHubById(requestDTO.getHubId());
                log.info("[Batch] 使用前端指定Hub: id={}", requestDTO.getHubId());
            } else {
                // LLM 智能选择：综合距离、今日负载、历史交通
                double centerLat = vrpResult.getOrderedCoordinates().stream()
                        .mapToDouble(c -> c[0]).average().orElse(requestDTO.getWarehouseLat());
                double centerLng = vrpResult.getOrderedCoordinates().stream()
                        .mapToDouble(c -> c[1]).average().orElse(requestDTO.getWarehouseLng());

                hubSelectionResult = llmHubSelectionService.selectHub(
                        centerLat, centerLng,
                        requestDTO.getOrderIds().size(),
                        requestDTO.getPlannedShipTime());

                hub = hubSelectionResult.getSelectedHub();
                log.info("[Batch] LLM选Hub结果: hub={}, llmEnhanced={}, reason={}",
                        hub != null ? hub.getName() : "无",
                        hubSelectionResult.getLlmEnhanced(),
                        hubSelectionResult.getReason());
            }
            if (hub == null) {
                log.warn("[Batch] 未找到可用Hub，降级为单段多点配送模式");
                useHub = false;
            }
        }

        // ── 3. 写 logistics_batch 记录 ────────────────────────────
        LogisticsBatch batch = new LogisticsBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setWarehouseId(requestDTO.getWarehouseId());
        batch.setHubId(hub != null ? hub.getId() : null);
        batch.setBatchStatus(0);
        batch.setTotalOrders(requestDTO.getOrderIds().size());
        batch.setUseHub(useHub ? 1 : 0);
        batch.setVrpAlgorithm(vrpResult.getAlgorithm());
        batch.setTotalDistance(vrpResult.getTotalDistanceM());
        batchMapper.insert(batch);
        log.info("[Batch] 批次创建，batchId={}, batchNo={}", batch.getId(), batch.getBatchNo());

        // ── 4. 创建干线路线（仓库/全国Hub → 城市Hub，segment_type=1）────────────
        LogisticsRoute trunkRoute = null;
        if (useHub) {
            CreateRouteRequestDTO trunkReq = new CreateRouteRequestDTO();
            trunkReq.setOrderId(null);  // 干线路线不关联具体订单，order_id 留 NULL
            trunkReq.setWarehouseId(requestDTO.getWarehouseId());
            trunkReq.setBatchId(batch.getId());
            trunkReq.setSegmentType(1);
            trunkReq.setHubId(hub.getId());
            trunkReq.setStartAddress(requestDTO.getWarehouseAddress());
            trunkReq.setStartLatitude(requestDTO.getWarehouseLat());
            trunkReq.setStartLongitude(requestDTO.getWarehouseLng());
            trunkReq.setEndAddress(hub.getAddress());
            trunkReq.setEndLatitude(hub.getLatitude());
            trunkReq.setEndLongitude(hub.getLongitude());
            trunkReq.setPlannedShipTime(requestDTO.getPlannedShipTime());
            trunkRoute = routeService.createSegmentRoute(trunkReq);

            // 回写干线路线ID到批次
            batch.setTrunkRouteId(trunkRoute.getId());
            batchMapper.updateById(batch);
            log.info("[Batch] 干线路线创建，routeId={}", trunkRoute.getId());

            // 通知 driver-service 创建干线配送记录
            LastMileActivateMessage trunkMsg = new LastMileActivateMessage();
            trunkMsg.setRouteId(trunkRoute.getId());
            trunkMsg.setOrderId(null);              // 干线无具体订单
            trunkMsg.setBatchId(batch.getId());
            trunkMsg.setHubId(hub.getId());
            trunkMsg.setEndAddress(hub.getAddress());
            trunkMsg.setEndLat(hub.getLatitude());
            trunkMsg.setEndLng(hub.getLongitude());
            trunkMsg.setSegmentType(1);             // 干线
            trunkMsg.setPreAssignedDriverId(null);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ROUTING_LAST_MILE_ACTIVATE,
                    trunkMsg
            );
            log.info("[Batch] 已通知 driver-service 创建干线配送记录，routeId={}", trunkRoute.getId());
        }

        // ── 5. 末端 K-Means 分组 + 多停靠末端路线创建 ──────────────
        //
        //  流程：
        //    a. 将 VRP 结果构建为 StopInfo 列表（含全局顺序）
        //    b. K-Means 按地理位置分组（k = ceil(N / maxStopsPerDriver)）
        //    c. 每组内按 VRP 全局顺序排列停靠点（保持最优访问顺序）
        //    d. 每组创建一条多停靠末端路线（stop_count = 组内订单数）
        //    e. 每个订单写入一条 batch_item，记录其所属组路线和组内停靠序号
        //
        List<LogisticsBatchItem> items = new ArrayList<>();
        List<LogisticsRoute> lastMileRoutes = new ArrayList<>();
        List<Long> orderedOrderIds = vrpResult.getOrderedOrderIds();
        List<double[]> orderedCoords = vrpResult.getOrderedCoordinates();
        List<String> orderedAddresses = vrpResult.getOrderedAddresses();
        List<String> orderedNames = vrpResult.getOrderedReceiverNames();
        List<String> orderedPhones = vrpResult.getOrderedReceiverPhones();

        // isCrossCity 仅表示货物来自全国 Hub（干线起点为全国 Hub），不影响末端是否经内部分拨。
        // 末端起点由 useHub 统一决定：
        //   useHub=true  → 始终经城市内 logistics_hub（分拨中心），无论跨城与否
        //   useHub=false → 直接从货源地（仓库或全国 Hub）出发，不经内部分拨
        boolean isCrossCity = Boolean.TRUE.equals(requestDTO.getIsCrossCity());
        String lastMileStartAddress = useHub ? hub.getAddress() : requestDTO.getWarehouseAddress();
        Double lastMileStartLat     = useHub ? hub.getLatitude()  : requestDTO.getWarehouseLat();
        Double lastMileStartLng     = useHub ? hub.getLongitude() : requestDTO.getWarehouseLng();
        // 经内部 Hub 或跨城到达时均为 segment_type=2（末端配送）；直送为 0
        int    segType = (useHub || isCrossCity) ? 2 : 0;

        // a. 构建 StopInfo 列表（保留 VRP 全局顺序）
        int n = orderedOrderIds.size();
        double[] stopLats  = new double[n];
        double[] stopLngs  = new double[n];
        for (int i = 0; i < n; i++) {
            stopLats[i] = orderedCoords.get(i)[0];
            stopLngs[i] = orderedCoords.get(i)[1];
        }

        // b. 计算分组数 k（每名末端司机最多 4 个停靠点）
        int k = Math.max(1, (int) Math.ceil((double) n / 4));
        k = Math.min(k, n);

        // c. K-Means 聚类（Haversine 距离）
        int[] groupAssignments = kMeansGroup(stopLats, stopLngs, k);

        // d. 将每个下标分配到对应组（组内按 VRP 原始顺序，即 i 从小到大排列）
        List<List<Integer>> groups = new ArrayList<>();
        for (int g = 0; g < k; g++) groups.add(new ArrayList<>());
        for (int i = 0; i < n; i++) groups.get(groupAssignments[i]).add(i);
        // 过滤空组
        groups.removeIf(List::isEmpty);

        // e. 每组创建一条末端路线
        for (int gi = 0; gi < groups.size(); gi++) {
            List<Integer> groupIdx = groups.get(gi);
            int stopCount = groupIdx.size();
            // 最后一个停靠点作为路线 endAddress
            int lastIdx  = groupIdx.get(stopCount - 1);

            // 构建 waypoints JSON
            StringBuilder wpJson = new StringBuilder("[");
            for (int s = 0; s < stopCount; s++) {
                int idx = groupIdx.get(s);
                if (s > 0) wpJson.append(",");
                wpJson.append(String.format(
                    "{\"seq\":%d,\"orderId\":%d,\"address\":\"%s\","
                    + "\"lat\":%.6f,\"lng\":%.6f,"
                    + "\"receiverName\":\"%s\",\"receiverPhone\":\"%s\"}",
                    s + 1,
                    orderedOrderIds.get(idx),
                    escapeJson(orderedAddresses.get(idx)),
                    stopLats[idx], stopLngs[idx],
                    escapeJson(orderedNames.get(idx)),
                    escapeJson(orderedPhones.get(idx))
                ));
            }
            wpJson.append("]");

            // 单停靠点时 orderId = 具体订单，多停靠时 orderId = null
            Long routeOrderId = (stopCount == 1) ? orderedOrderIds.get(groupIdx.get(0)) : null;

            CreateRouteRequestDTO req = new CreateRouteRequestDTO();
            req.setOrderId(routeOrderId);
            req.setWarehouseId(requestDTO.getWarehouseId());
            req.setBatchId(batch.getId());
            req.setSegmentType(segType);
            req.setHubId(hub != null ? hub.getId() : null);
            req.setStartAddress(lastMileStartAddress);
            req.setStartLatitude(lastMileStartLat);
            req.setStartLongitude(lastMileStartLng);
            req.setEndAddress(orderedAddresses.get(lastIdx));
            req.setEndLatitude(stopLats[lastIdx]);
            req.setEndLongitude(stopLngs[lastIdx]);
            req.setGroupIndex(gi);
            req.setStopCount(stopCount);
            req.setWaypoints(stopCount > 1 ? wpJson.toString() : null);
            req.setPlannedShipTime(requestDTO.getPlannedShipTime());
            // Hub 批次（含跨城）：末端路线始终从 routeStatus=-1 开始，等干线到达 Hub 后由 activateLastMileRoutes 激活
            // 直送批次（!useHub）才设为 true（路线立即可出发）
            req.setCrossCityDirect(!useHub);

            LogisticsRoute groupRoute = routeService.createSegmentRoute(req);
            lastMileRoutes.add(groupRoute);
            log.info("[Batch] 末端组路线创建 groupIndex={}, stopCount={}, routeId={}",
                    gi, stopCount, groupRoute.getId());

            // 每个订单写一条 batch_item，记录组内停靠序号
            for (int s = 0; s < stopCount; s++) {
                int idx = groupIdx.get(s);
                LogisticsBatchItem item = new LogisticsBatchItem();
                item.setBatchId(batch.getId());
                item.setOrderId(orderedOrderIds.get(idx));
                item.setRouteId(groupRoute.getId());
                item.setVisitSequence(idx + 1);       // 全局 VRP 顺序
                item.setStopSequence(s + 1);           // 组内停靠顺序
                item.setEndLat(stopLats[idx]);
                item.setEndLng(stopLngs[idx]);
                item.setEndAddress(orderedAddresses.get(idx));
                item.setReceiverName(orderedNames.get(idx));
                item.setReceiverPhone(orderedPhones.get(idx));
                item.setItemStatus(0);
                batchItemMapper.insert(item);
                items.add(item);
            }
        }

        // ── 5b. 直接送达：路线已就绪（routeStatus=0），立即通知 driver-service 创建配送单 ──
        if (!useHub) {
            for (LogisticsRoute route : lastMileRoutes) {
                int stopCount = route.getStopCount() != null ? route.getStopCount() : 1;
                String displayAddr = stopCount > 1
                        ? "共" + stopCount + "个停靠点，终点：" + route.getEndAddress()
                        : route.getEndAddress();
                LastMileActivateMessage msg = new LastMileActivateMessage();
                msg.setRouteId(route.getId());
                msg.setOrderId(route.getOrderId());     // 多停靠时为 null
                msg.setBatchId(batch.getId());
                msg.setHubId(null);                     // 直送无中转站
                msg.setEndAddress(displayAddr);
                msg.setEndLat(route.getEndLatitude());
                msg.setEndLng(route.getEndLongitude());
                msg.setSegmentType(2);
                msg.setPreAssignedDriverId(route.getDriverId()); // 管理员预分配
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE,
                        RabbitMQConfig.ROUTING_LAST_MILE_ACTIVATE,
                        msg
                );
                log.info("[Batch] 直送路线已激活，routeId={}, stopCount={}", route.getId(), stopCount);
            }
            // 直送无干线等待，直接进入末端派送中状态
            batch.setBatchStatus(3);
            batchMapper.updateById(batch);
        }

        // ── 6. 组装并返回 BatchDetailDTO ──────────────────────────
        BatchDetailDTO result = new BatchDetailDTO();
        result.setBatch(batch);
        result.setVrpResult(vrpResult);
        result.setItems(items);
        if (hub != null) {
            HubDTO hubDTO = new HubDTO();
            hubDTO.setId(hub.getId());
            hubDTO.setName(hub.getName());
            hubDTO.setAddress(hub.getAddress());
            hubDTO.setLatitude(hub.getLatitude());
            hubDTO.setLongitude(hub.getLongitude());
            hubDTO.setRegion(hub.getRegion());
            result.setHub(hubDTO);
        }
        // 方案C：将 LLM Hub 选择结果写入响应（非持久化，仅本次返回）
        if (hubSelectionResult != null) {
            result.setHubSelectionReason(hubSelectionResult.getReason());
            result.setHubSelectionConfidence(hubSelectionResult.getConfidenceLevel());
            result.setHubSelectionLlmEnhanced(hubSelectionResult.getLlmEnhanced());
            result.setHubCandidates(hubSelectionResult.getCandidates());
        }
        if (trunkRoute != null) {
            RouteDetailDTO trunkDetail = new RouteDetailDTO();
            trunkDetail.setRoute(trunkRoute);
            result.setTrunkRoute(trunkDetail);
        }
        List<RouteDetailDTO> lastMileDetails = new ArrayList<>();
        for (LogisticsRoute r : lastMileRoutes) {
            RouteDetailDTO d = new RouteDetailDTO();
            d.setRoute(r);
            lastMileDetails.add(d);
        }
        result.setLastMileRoutes(lastMileDetails);

        log.info("[Batch] 批次创建完成，batchId={}, 干线路线={}, 末端路线数={}",
                batch.getId(), trunkRoute != null ? trunkRoute.getId() : "无", lastMileRoutes.size());
        return result;
    }

    // ----------------------------------------------------------------
    //  查询批次详情
    // ----------------------------------------------------------------

    @Override
    public BatchDetailDTO getBatchDetail(Long batchId) {
        LogisticsBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new RuntimeException("批次不存在：id=" + batchId);

        BatchDetailDTO result = new BatchDetailDTO();
        result.setBatch(batch);
        result.setItems(batchItemMapper.selectByBatchId(batchId));

        if (batch.getHubId() != null) {
            LogisticsHub hub = hubMapper.selectById(batch.getHubId());
            if (hub != null) {
                HubDTO hubDTO = new HubDTO();
                hubDTO.setId(hub.getId());
                hubDTO.setName(hub.getName());
                hubDTO.setAddress(hub.getAddress());
                hubDTO.setLatitude(hub.getLatitude());
                hubDTO.setLongitude(hub.getLongitude());
                hubDTO.setRegion(hub.getRegion());
                result.setHub(hubDTO);
            }
        }

        if (batch.getTrunkRouteId() != null) {
            LogisticsRoute trunk = routeMapper.selectById(batch.getTrunkRouteId());
            if (trunk != null) {
                RouteDetailDTO d = new RouteDetailDTO();
                d.setRoute(trunk);
                result.setTrunkRoute(d);
            }
        }

        // segmentType: 0=直送末端, 1=干线, 2=Hub末端 → 排除干线(1)即可覆盖所有末端
        List<LogisticsRoute> lastMileRoutes = routeMapper.selectList(
                new LambdaQueryWrapper<LogisticsRoute>()
                        .eq(LogisticsRoute::getBatchId, batchId)
                        .ne(LogisticsRoute::getSegmentType, 1)
                        .orderByAsc(LogisticsRoute::getCreateTime));
        List<RouteDetailDTO> lastMileDetails = new ArrayList<>();
        for (LogisticsRoute r : lastMileRoutes) {
            RouteDetailDTO d = new RouteDetailDTO();
            d.setRoute(r);
            lastMileDetails.add(d);
        }
        result.setLastMileRoutes(lastMileDetails);

        return result;
    }

    // ----------------------------------------------------------------
    //  激活末端路线（干线到Hub后调用）
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public void activateLastMileRoutes(Long batchId) {
        log.info("[Batch] 激活末端路线，batchId={}", batchId);

        // 更新批次状态 → 已到中转站(2)
        LogisticsBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            log.error("[Batch] 批次不存在：batchId={}", batchId);
            return;
        }
        batch.setBatchStatus(2);
        batchMapper.updateById(batch);

        // 查询所有末端路线（待激活状态 -1）
        List<LogisticsRoute> lastMileRoutes = routeMapper.selectList(
                new LambdaQueryWrapper<LogisticsRoute>()
                        .eq(LogisticsRoute::getBatchId, batchId)
                        .eq(LogisticsRoute::getSegmentType, 2)
                        .eq(LogisticsRoute::getRouteStatus, -1));

        log.info("[DEBUG][activateLastMileRoutes] batchId={}, 找到 {} 条待激活末端路线", batchId, lastMileRoutes.size());

        for (LogisticsRoute route : lastMileRoutes) {
            log.info("[DEBUG][activateLastMileRoutes] 处理路线 routeId={}, segmentType={}, routeStatus={}, driverId={}",
                    route.getId(), route.getSegmentType(), route.getRouteStatus(), route.getDriverId());

            // 激活路线：-1 → 0（待出发）
            route.setRouteStatus(0);
            routeMapper.updateById(route);

            int stopCount = route.getStopCount() != null ? route.getStopCount() : 1;
            String displayAddr = stopCount > 1
                    ? "共" + stopCount + "个停靠点，终点：" + route.getEndAddress()
                    : route.getEndAddress();

            LastMileActivateMessage msg = new LastMileActivateMessage();
            msg.setRouteId(route.getId());
            msg.setOrderId(route.getOrderId());
            msg.setBatchId(batchId);
            msg.setHubId(route.getHubId());
            msg.setEndAddress(displayAddr);
            msg.setEndLat(route.getEndLatitude());
            msg.setEndLng(route.getEndLongitude());
            msg.setSegmentType(2);
            msg.setPreAssignedDriverId(route.getDriverId());

            log.info("[DEBUG][activateLastMileRoutes] 发送 MQ #14: routeId={}, preAssignedDriverId={}",
                    route.getId(), route.getDriverId());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ROUTING_LAST_MILE_ACTIVATE,
                    msg
            );
        }

        // 更新批次状态 → 末端派送中(3)
        batch.setBatchStatus(3);
        batchMapper.updateById(batch);
    }

    // ----------------------------------------------------------------
    //  检查批次是否全部完成
    // ----------------------------------------------------------------

    @Override
    public void checkAndUpdateBatchStatus(Long batchId) {
        long pendingCount = batchItemMapper.selectCount(
                new LambdaQueryWrapper<LogisticsBatchItem>()
                        .eq(LogisticsBatchItem::getBatchId, batchId)
                        .ne(LogisticsBatchItem::getItemStatus, 2));

        if (pendingCount == 0) {
            batchMapper.update(null,
                    new LambdaUpdateWrapper<LogisticsBatch>()
                            .eq(LogisticsBatch::getId, batchId)
                            .set(LogisticsBatch::getBatchStatus, 4));
            log.info("[Batch] 批次全部完成，batchId={}", batchId);
        }
    }

    // ----------------------------------------------------------------
    //  辅助方法
    // ----------------------------------------------------------------

    private String generateBatchNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 10000);
        return "LB" + time + String.format("%04d", rand);
    }

    // 以下 extract* 方法从请求DTO中提取各字段列表
    // 实际项目中，这些坐标信息通过 Feign 调用 order-service 获取
    // 此处简化为从 DTO 中直接获取（前端在调用时已查询好）

    private List<Double> extractLats(CreateBatchRequestDTO dto) {
        return dto.getOrderIds().stream()
                .map(id -> getOrderEndLat(dto, id))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<Double> extractLngs(CreateBatchRequestDTO dto) {
        return dto.getOrderIds().stream()
                .map(id -> getOrderEndLng(dto, id))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<String> extractAddresses(CreateBatchRequestDTO dto) {
        return dto.getOrderIds().stream()
                .map(id -> getOrderAddress(dto, id))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<String> extractReceiverNames(CreateBatchRequestDTO dto) {
        return dto.getOrderIds().stream()
                .map(id -> getReceiverName(dto, id))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<String> extractReceiverPhones(CreateBatchRequestDTO dto) {
        return dto.getOrderIds().stream()
                .map(id -> getReceiverPhone(dto, id))
                .collect(java.util.stream.Collectors.toList());
    }

    private Double getOrderEndLat(CreateBatchRequestDTO dto, Long orderId) {
        if (dto.getOrderItems() != null) {
            return dto.getOrderItems().stream()
                    .filter(item -> item.getOrderId().equals(orderId))
                    .map(CreateBatchRequestDTO.OrderItem::getEndLat)
                    .findFirst().orElse(0.0);
        }
        return 0.0;
    }

    private Double getOrderEndLng(CreateBatchRequestDTO dto, Long orderId) {
        if (dto.getOrderItems() != null) {
            return dto.getOrderItems().stream()
                    .filter(item -> item.getOrderId().equals(orderId))
                    .map(CreateBatchRequestDTO.OrderItem::getEndLng)
                    .findFirst().orElse(0.0);
        }
        return 0.0;
    }

    private String getOrderAddress(CreateBatchRequestDTO dto, Long orderId) {
        if (dto.getOrderItems() != null) {
            return dto.getOrderItems().stream()
                    .filter(item -> item.getOrderId().equals(orderId))
                    .map(CreateBatchRequestDTO.OrderItem::getEndAddress)
                    .findFirst().orElse("");
        }
        return "";
    }

    private String getReceiverName(CreateBatchRequestDTO dto, Long orderId) {
        if (dto.getOrderItems() != null) {
            return dto.getOrderItems().stream()
                    .filter(item -> item.getOrderId().equals(orderId))
                    .map(CreateBatchRequestDTO.OrderItem::getReceiverName)
                    .findFirst().orElse("");
        }
        return "";
    }

    private String getReceiverPhone(CreateBatchRequestDTO dto, Long orderId) {
        if (dto.getOrderItems() != null) {
            return dto.getOrderItems().stream()
                    .filter(item -> item.getOrderId().equals(orderId))
                    .map(CreateBatchRequestDTO.OrderItem::getReceiverPhone)
                    .findFirst().orElse("");
        }
        return "";
    }

    // ----------------------------------------------------------------
    //  末端 K-Means 聚类（Haversine 距离，最多 MAX_ITER 轮）
    //  返回每个停靠点的组编号数组（与输入 stopLats/stopLngs 对应）
    // ----------------------------------------------------------------

    private static final int    KMEANS_MAX_ITER = 50;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private int[] kMeansGroup(double[] lats, double[] lngs, int k) {
        int n = lats.length;
        if (k >= n) {
            int[] trivial = new int[n];
            for (int i = 0; i < n; i++) trivial[i] = i;
            return trivial;
        }

        // 初始化：均匀间隔选 k 个中心
        double[] cLat = new double[k];
        double[] cLng = new double[k];
        for (int j = 0; j < k; j++) {
            int idx = (int) Math.round((double) j / (k - 1 == 0 ? 1 : k - 1) * (n - 1));
            cLat[j] = lats[idx];
            cLng[j] = lngs[idx];
        }

        int[] assign = new int[n];
        for (int iter = 0; iter < KMEANS_MAX_ITER; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int best = 0; double bestD = Double.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    double d = haversineKm(lats[i], lngs[i], cLat[j], cLng[j]);
                    if (d < bestD) { bestD = d; best = j; }
                }
                if (assign[i] != best) { assign[i] = best; changed = true; }
            }
            if (!changed) break;
            // 重新计算中心
            double[] sLat = new double[k], sLng = new double[k];
            int[] cnt = new int[k];
            for (int i = 0; i < n; i++) {
                sLat[assign[i]] += lats[i]; sLng[assign[i]] += lngs[i]; cnt[assign[i]]++;
            }
            for (int j = 0; j < k; j++) {
                if (cnt[j] > 0) { cLat[j] = sLat[j] / cnt[j]; cLng[j] = sLng[j] / cnt[j]; }
            }
        }
        return assign;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 跨城干线到达后，从目标 Hub 出发，为该批次内的订单发起末端 VRP 调度。
     * 实质上就是以 destHub 坐标为 warehouse 起点，调用 createBatch 逻辑。
     */
    @Override
    @Transactional
    public void activateFromInterCityArrival(Long interCityBatchId, Long destHubId) {
        // 仅处理「归属本干线批次且最终目的 Hub = 本段到达 Hub」的订单（避免误把经停城市其他单拉进来）
        List<Long> inBatch = orderInfoMapper.selectOrderIdsByBatchAndFinalDestHub(interCityBatchId, destHubId);
        if (inBatch.isEmpty()) {
            log.info("[activateFromInterCityArrival] 批次{}在Hub{}无待末端订单（或均为中转目的）", interCityBatchId, destHubId);
            return;
        }
        List<DispatchPool> arrivedOrders = dispatchPoolMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DispatchPool>()
                        .in(DispatchPool::getOrderId, inBatch)
                        .eq(DispatchPool::getStatus, 0));
        if (arrivedOrders.isEmpty()) {
            log.warn("[activateFromInterCityArrival] 批次{}有{}笔订单但调度池无待调度行，请确认干线到达是否已写入 dispatch_pool",
                    interCityBatchId, inBatch.size());
            return;
        }

        // 取目标 Hub 信息作为"虚拟仓库"起点
        LogisticsHub destHub = hubMapper.selectById(destHubId);
        if (destHub == null) {
            log.warn("[activateFromInterCityArrival] destHub {}不存在", destHubId);
            return;
        }

        // 构建 CreateBatchRequestDTO，以 Hub 坐标为起点
        CreateBatchRequestDTO req = new CreateBatchRequestDTO();
        req.setWarehouseId(null); // 无仓库，起点为 Hub
        req.setWarehouseAddress(destHub.getAddress());
        req.setWarehouseLat(destHub.getLatitude());
        req.setWarehouseLng(destHub.getLongitude());
        req.setUseHub(false); // 末端不再走Hub，直接配送
        req.setOrderIds(arrivedOrders.stream()
                .map(DispatchPool::getOrderId)
                .collect(java.util.stream.Collectors.toList()));

        // 填充 orderItems（从 dispatch_pool 取各订单收货坐标）
        List<CreateBatchRequestDTO.OrderItem> orderItems = arrivedOrders.stream().map(pool -> {
            CreateBatchRequestDTO.OrderItem oi = new CreateBatchRequestDTO.OrderItem();
            oi.setOrderId(pool.getOrderId());
            oi.setEndLat(pool.getEndLat());
            oi.setEndLng(pool.getEndLng());
            oi.setEndAddress(pool.getEndAddress());
            oi.setReceiverName(pool.getReceiverName());
            oi.setReceiverPhone(pool.getReceiverPhone());
            return oi;
        }).collect(java.util.stream.Collectors.toList());
        req.setOrderItems(orderItems);

        log.info("[activateFromInterCityArrival] 批次{}到达Hub{}，触发{}个订单末端调度",
                interCityBatchId, destHubId, arrivedOrders.size());
        createBatch(req);
    }
}
