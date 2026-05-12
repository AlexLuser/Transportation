package com.fm.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.logistics.dto.CreateRouteRequestDTO;
import com.fm.logistics.dto.CreateRouteResponseDTO;
import org.springframework.dao.DuplicateKeyException;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.entity.LogisticsNode;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.entity.LogisticsBatchItem;
import com.fm.logistics.mapper.LogisticsBatchItemMapper;
import com.fm.logistics.mapper.LogisticsNodeMapper;
import com.fm.logistics.mapper.LogisticsRouteMapper;
import com.fm.logistics.mapper.LogisticsTrackMapper;
import com.fm.logistics.dto.RouteResultDTO;
import com.fm.logistics.service.LogisticsRouteService;
import com.fm.logistics.service.RoutePlanningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class LogisticsRouteServiceImpl implements LogisticsRouteService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsRouteServiceImpl.class);

    @Autowired
    private LogisticsRouteMapper logisticsRouteMapper;

    @Autowired
    private LogisticsNodeMapper logisticsNodeMapper;

    @Autowired
    private LogisticsTrackMapper logisticsTrackMapper;

    @Autowired
    private RoutePlanningService routePlanningService;

    @Autowired
    private LogisticsBatchItemMapper batchItemMapper;

    @Autowired
    private com.fm.logistics.feign.DriverFeignClient driverFeignClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // ----------------------------------------------------------------
    //  创建路线
    // ----------------------------------------------------------------

    /**
     * 创建物流路线（幂等）
     *
     * 设计要点：
     *  1. LLM/A* 路线规划在事务外执行，避免长事务（规划可能耗时 5~30s）持有 DB 连接，
     *     从而消除并发场景下 MyBatis L1 缓存读旧快照导致补救 SELECT 仍返回 0 的问题。
     *  2. 实际 DB 写操作用 TransactionTemplate 包裹，事务窗口缩短为毫秒级，
     *     并发竞争概率极低；即便出现竞争，DuplicateKeyException 兜底后再次 SELECT
     *     使用全新 SqlSession（空缓存），可正确读到已提交数据。
     */
    @Override
    public CreateRouteResponseDTO createRoute(CreateRouteRequestDTO requestDTO) {
        // ── 1. 快速前置检查（无事务，不持有 DB 连接）──
        LogisticsRoute existing = logisticsRouteMapper.selectOne(
                new LambdaQueryWrapper<LogisticsRoute>().eq(LogisticsRoute::getOrderId, requestDTO.getOrderId()));
        if (existing != null) {
            return CreateRouteResponseDTO.existingRoute(existing);
        }

        // ── 2. 构建路线对象（无事务）──
        LogisticsRoute route = new LogisticsRoute();
        route.setRouteNo(generateRouteNo());
        route.setOrderId(requestDTO.getOrderId());
        route.setWarehouseId(requestDTO.getWarehouseId());
        route.setStartAddress(requestDTO.getStartAddress());
        route.setStartLatitude(requestDTO.getStartLatitude());
        route.setStartLongitude(requestDTO.getStartLongitude());
        route.setEndAddress(requestDTO.getEndAddress());
        route.setEndLatitude(requestDTO.getEndLatitude());
        route.setEndLongitude(requestDTO.getEndLongitude());
        route.setReceiverName(requestDTO.getReceiverName());
        route.setReceiverPhone(requestDTO.getReceiverPhone());
        route.setRouteStatus(0);

        // ── 3. 路径规划（耗时操作，在事务外执行，不阻塞 DB 连接）──
        RouteResultDTO planResult = null;
        if (route.getStartLatitude() != null && route.getStartLongitude() != null
                && route.getEndLatitude() != null && route.getEndLongitude() != null) {
            planResult = routePlanningService.planRoute(
                    route.getStartLatitude(), route.getStartLongitude(),
                    route.getEndLatitude(),   route.getEndLongitude(),
                    requestDTO.getPlannedShipTime()
            );
            if (planResult != null && planResult.isSuccess()) {
                route.setPlannedRoute(planResult.getRoutePoints().toJsonString());
                route.setEstimatedArrivalTime(
                        new Date(System.currentTimeMillis() + planResult.getDurationMs()));
            }
        }

        // ── 4. 短事务：仅包含 DB 写操作（毫秒级，全新 SqlSession，空缓存）──
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        final RouteResultDTO planResultFinal = planResult;
        return txTemplate.execute(status -> {
            // 事务内二次检查（全新 SqlSession，不受前置检查缓存影响）
            LogisticsRoute recheck = logisticsRouteMapper.selectOne(
                    new LambdaQueryWrapper<LogisticsRoute>().eq(LogisticsRoute::getOrderId, requestDTO.getOrderId()));
            if (recheck != null) {
                return CreateRouteResponseDTO.existingRoute(recheck);
            }

            try {
                logisticsRouteMapper.insert(route);
            } catch (DuplicateKeyException e) {
                // 极小并发窗口兜底：INSERT 前 clearLocalCache 已清空缓存，
                // 此处 SELECT 使用同一 SqlSession 的空缓存直接读库，可见已提交行
                LogisticsRoute committed = logisticsRouteMapper.selectOne(
                        new LambdaQueryWrapper<LogisticsRoute>().eq(LogisticsRoute::getOrderId, requestDTO.getOrderId()));
                if (committed != null) {
                    return CreateRouteResponseDTO.existingRoute(committed);
                }
                throw e;
            }

            createDefaultNodes(route);
            return CreateRouteResponseDTO.of(route, planResultFinal);
        });
    }

    // ----------------------------------------------------------------
    //  批次分段路线创建（Hub-and-Spoke 专用）
    // ----------------------------------------------------------------

    /**
     * 创建分段路线（segment_type=1干线 或 segment_type=2末端）
     *
     * 与 createRoute() 的区别：
     *   - 不做幂等检查（批次内每条路线都是独立的）
     *   - 写入 batchId / segmentType / hubId 字段
     *   - 末端路线初始状态为 -1（待激活），需等干线到Hub后激活
     *   - 干线路线终点是 Hub，末端路线起点是 Hub
     */
    @Override
    @Transactional
    public LogisticsRoute createSegmentRoute(CreateRouteRequestDTO requestDTO) {
        int segmentType = requestDTO.getSegmentType() != null ? requestDTO.getSegmentType() : 0;

        LogisticsRoute route = new LogisticsRoute();
        route.setRouteNo(generateRouteNo());
        route.setOrderId(requestDTO.getOrderId());
        route.setWarehouseId(requestDTO.getWarehouseId());
        route.setBatchId(requestDTO.getBatchId());
        route.setSegmentType(segmentType);
        route.setHubId(requestDTO.getHubId());
        route.setStartAddress(requestDTO.getStartAddress());
        route.setStartLatitude(requestDTO.getStartLatitude());
        route.setStartLongitude(requestDTO.getStartLongitude());
        route.setEndAddress(requestDTO.getEndAddress());
        route.setEndLatitude(requestDTO.getEndLatitude());
        route.setEndLongitude(requestDTO.getEndLongitude());
        // 多停靠末端路线字段
        int stopCount = requestDTO.getStopCount() != null ? requestDTO.getStopCount() : 1;
        route.setStopCount(stopCount);
        route.setGroupIndex(requestDTO.getGroupIndex());
        route.setWaypoints(requestDTO.getWaypoints());
        // 末端路线初始状态 -1（待激活，等干线到达 Hub 后激活）；
        // 跨城到达批次例外：全国 Hub 已到达，末端路线立即置为 0（待出发）；
        // 干线和普通路线为 0（待出发）
        boolean crossCityDirect = Boolean.TRUE.equals(requestDTO.getCrossCityDirect());
        route.setRouteStatus((segmentType == 2 && !crossCityDirect) ? -1 : 0);

        // 路线规划：多停靠使用 planMultiStop（经过所有中间停靠点），单停靠用 planRoute
        if (route.getStartLatitude() != null && route.getStartLongitude() != null
                && route.getEndLatitude() != null && route.getEndLongitude() != null) {
            RouteResultDTO planResult;
            if (stopCount > 1 && requestDTO.getWaypoints() != null) {
                // 解析 waypoints JSON，构建完整路点列表：[Hub] + [stop1, stop2, ..., stopN]
                List<double[]> coords = new ArrayList<>();
                coords.add(new double[]{route.getStartLatitude(), route.getStartLongitude()});
                boolean parseOk = false;
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode wps = mapper.readTree(requestDTO.getWaypoints());
                    java.util.List<JsonNode> sorted = new java.util.ArrayList<>();
                    wps.forEach(sorted::add);
                    sorted.sort(java.util.Comparator.comparingInt(n -> n.path("seq").asInt()));
                    sorted.forEach(wp -> coords.add(new double[]{
                            wp.path("lat").asDouble(), wp.path("lng").asDouble()
                    }));
                    parseOk = coords.size() >= 2;
                } catch (Exception e) {
                    log.warn("[createSegmentRoute] 解析 waypoints 失败，降级为点对点规划: {}", e.getMessage());
                }
                if (parseOk) {
                    planResult = routePlanningService.planMultiStop(coords, requestDTO.getPlannedShipTime());
                } else {
                    planResult = routePlanningService.planRoute(
                            route.getStartLatitude(), route.getStartLongitude(),
                            route.getEndLatitude(),   route.getEndLongitude(),
                            requestDTO.getPlannedShipTime());
                }
            } else {
                planResult = routePlanningService.planRoute(
                        route.getStartLatitude(), route.getStartLongitude(),
                        route.getEndLatitude(),   route.getEndLongitude(),
                        requestDTO.getPlannedShipTime());
            }
            if (planResult != null && planResult.isSuccess()) {
                route.setPlannedRoute(planResult.getRoutePoints().toJsonString());
                route.setEstimatedArrivalTime(
                        new Date(System.currentTimeMillis() + planResult.getDurationMs()));
            }
        }

        logisticsRouteMapper.insert(route);

        // 多停靠末端路线（stop_count>1）使用 waypoints JSON 记录各停靠点，
        // 不再冗余创建 logistics_node 行。单停靠路线保留原有节点创建逻辑。
        if (stopCount <= 1) {
            createSegmentNodes(route, segmentType);
        }

        return route;
    }

    /**
     * 激活末端路线（干线到Hub后调用）
     * 将路线状态从 -1（待激活）改为 0（待出发）
     */
    @Override
    @Transactional
    public void activateLastMileRoute(Long routeId) {
        LogisticsRoute route = logisticsRouteMapper.selectById(routeId);
        if (route != null && route.getRouteStatus() == -1) {
            route.setRouteStatus(0);
            logisticsRouteMapper.updateById(route);
        }
    }

    @Override
    public List<LogisticsRoute> getRoutesByBatchId(Long batchId) {
        return logisticsRouteMapper.selectList(
                new LambdaQueryWrapper<LogisticsRoute>()
                        .eq(LogisticsRoute::getBatchId, batchId)
                        .orderByAsc(LogisticsRoute::getSegmentType)
                        .orderByAsc(LogisticsRoute::getCreateTime));
    }

    // ----------------------------------------------------------------
    //  查询路线详情（三种方式，共用同一个组装方法）
    // ----------------------------------------------------------------

    @Override
    public RouteDetailDTO getRouteDetailById(Long routeId) {
        LogisticsRoute route = getRouteOrThrow(routeId);
        return buildRouteDetailDTO(route);
    }

    @Override
    public RouteDetailDTO getRouteDetailByRouteNo(String routeNo) {
        LogisticsRoute route = logisticsRouteMapper.selectOne(
                new LambdaQueryWrapper<LogisticsRoute>().eq(LogisticsRoute::getRouteNo, routeNo));
        if (route == null) throw new BusinessException(ResultCode.FAIL.getCode(), "路线不存在");
        return buildRouteDetailDTO(route);
    }

    @Override
    @Cacheable(value = "logisticsRoute", key = "#orderId", unless = "#result == null")
    public RouteDetailDTO getRouteDetailByOrderId(Long orderId) {
        // 优先：旧体系 — logistics_route.order_id 直接匹配
        // 使用 last("LIMIT 1") + 按 id 倒序取最新一条，避免重复测试后出现多条导致 TooManyResultsException
        List<LogisticsRoute> routes = logisticsRouteMapper.selectList(
                new LambdaQueryWrapper<LogisticsRoute>()
                        .eq(LogisticsRoute::getOrderId, orderId)
                        .orderByDesc(LogisticsRoute::getId)
                        .last("LIMIT 1"));
        LogisticsRoute route = routes.isEmpty() ? null : routes.get(0);
        if (route != null) {
            return buildRouteDetailDTO(route);
        }

        // 回退：新智能调度体系 — 通过 logistics_batch_item 反查末端路线
        LogisticsBatchItem item = batchItemMapper.selectByOrderId(orderId);
        if (item == null || item.getRouteId() == null) {
            return null;  // 订单尚未调度，暂无路线
        }
        route = logisticsRouteMapper.selectById(item.getRouteId());
        if (route == null) {
            return null;
        }
        RouteDetailDTO dto = buildRouteDetailDTO(route);
        // 为多停靠路线附加本订单的停靠上下文，供顾客端展示
        if (route.getStopCount() != null && route.getStopCount() > 1) {
            dto.setStopSequence(item.getStopSequence());
            dto.setTotalStops(route.getStopCount());
            dto.setOrderEndAddress(item.getEndAddress());
            dto.setOrderEndLat(item.getEndLat());
            dto.setOrderEndLng(item.getEndLng());
        }
        return dto;
    }

    // ----------------------------------------------------------------
    //  状态变更
    // ----------------------------------------------------------------

    @Override
    @Transactional
    @CacheEvict(value = "logisticsRoute", allEntries = true)
    public LogisticsRoute bindDriver(Long routeId, Long driverId, Long deliveryId) {
        LogisticsRoute route = getRouteOrThrow(routeId);
        route.setDriverId(driverId);
        route.setDeliveryId(deliveryId);
        route.setRouteStatus(1);
        logisticsRouteMapper.updateById(route);
        return route;
    }

    @Override
    @Transactional
    @CacheEvict(value = "logisticsRoute", allEntries = true)
    public LogisticsRoute updateRouteStatus(Long routeId, Integer status) {
        LogisticsRoute route = getRouteOrThrow(routeId);
        route.setRouteStatus(status);
        if (status == 2) {
            route.setActualArrivalTime(new Date());
        }
        logisticsRouteMapper.updateById(route);
        return route;
    }

    // ----------------------------------------------------------------
    //  列表查询
    // ----------------------------------------------------------------

    @Override
    public List<LogisticsRoute> getPendingRoutes() {
        return logisticsRouteMapper.selectList(
                new LambdaQueryWrapper<LogisticsRoute>()
                        .eq(LogisticsRoute::getRouteStatus, 0)
                        .orderByAsc(LogisticsRoute::getCreateTime));
    }

    @Override
    public List<LogisticsRoute> getRoutesByDriverId(Long driverId) {
        return logisticsRouteMapper.selectList(
                new LambdaQueryWrapper<LogisticsRoute>()
                        .eq(LogisticsRoute::getDriverId, driverId)
                        .orderByDesc(LogisticsRoute::getCreateTime));
    }

    // ----------------------------------------------------------------
    //  私有方法
    // ----------------------------------------------------------------

    /** 查路线，不存在则抛异常（复用，避免重复代码） */
    private LogisticsRoute getRouteOrThrow(Long routeId) {
        LogisticsRoute route = logisticsRouteMapper.selectById(routeId);
        if (route == null) throw new BusinessException(ResultCode.FAIL.getCode(), "路线不存在");
        return route;
    }

    /** 组装路线详情 DTO（路线 + 节点列表 + 近期轨迹 + 状态描述 + 配送员信息） */
    private RouteDetailDTO buildRouteDetailDTO(LogisticsRoute route) {
        RouteDetailDTO dto = new RouteDetailDTO();
        dto.setRoute(route);
        dto.setNodes(logisticsNodeMapper.selectList(
                new LambdaQueryWrapper<LogisticsNode>()
                        .eq(LogisticsNode::getRouteId, route.getId())
                        .orderByAsc(LogisticsNode::getSequenceNo)));
        dto.setRecentTracks(logisticsTrackMapper.selectLatestTracks(route.getId(), 50));
        dto.setStatusDesc(getStatusDesc(route.getRouteStatus()));

        // 填充配送员姓名和电话（路线已绑定运输员时）
        if (route.getDriverId() != null) {
            try {
                com.fm.common.result.Result<java.util.Map<String, Object>> driverResult =
                        driverFeignClient.getDriverByDriverId(route.getDriverId());
                if (driverResult != null && driverResult.getCode() == 200 && driverResult.getData() != null) {
                    java.util.Map<String, Object> d = driverResult.getData();
                    dto.setDriverName(d.get("realName") != null ? d.get("realName").toString() : null);
                    dto.setDriverPhone(d.get("phone") != null ? d.get("phone").toString() : null);
                }
            } catch (Exception e) {
                // 获取配送员信息失败不影响主流程
                System.err.println("获取配送员信息失败: " + e.getMessage());
            }
        }

        return dto;
    }

    /**
     * 为分段路线创建节点
     * 干线（type=1）：出发点（仓库）+ 目的地（Hub，node_type=3）
     * 末端（type=2）：出发点（Hub，node_type=3）+ 目的地（客户）
     */
    private void createSegmentNodes(LogisticsRoute route, int segmentType) {
        if (segmentType == 1) {
            // 干线：仓库 → Hub
            LogisticsNode startNode = new LogisticsNode();
            startNode.setRouteId(route.getId());
            startNode.setNodeType(0);
            startNode.setNodeName("出发仓库");
            startNode.setNodeAddress(route.getStartAddress());
            startNode.setLatitude(route.getStartLatitude());
            startNode.setLongitude(route.getStartLongitude());
            startNode.setSequenceNo(0);
            startNode.setNodeStatus(0);
            logisticsNodeMapper.insert(startNode);

            LogisticsNode hubNode = new LogisticsNode();
            hubNode.setRouteId(route.getId());
            hubNode.setNodeType(3);  // 3=中转站
            hubNode.setNodeName("中转站");
            hubNode.setNodeAddress(route.getEndAddress());
            hubNode.setLatitude(route.getEndLatitude());
            hubNode.setLongitude(route.getEndLongitude());
            hubNode.setSequenceNo(99);
            hubNode.setNodeStatus(0);
            logisticsNodeMapper.insert(hubNode);

        } else if (segmentType == 2) {
            // 末端：Hub → 客户
            LogisticsNode hubNode = new LogisticsNode();
            hubNode.setRouteId(route.getId());
            hubNode.setNodeType(3);  // 3=中转站（起点）
            hubNode.setNodeName("中转站");
            hubNode.setNodeAddress(route.getStartAddress());
            hubNode.setLatitude(route.getStartLatitude());
            hubNode.setLongitude(route.getStartLongitude());
            hubNode.setSequenceNo(0);
            hubNode.setNodeStatus(0);
            logisticsNodeMapper.insert(hubNode);

            LogisticsNode endNode = new LogisticsNode();
            endNode.setRouteId(route.getId());
            endNode.setNodeType(2);
            endNode.setNodeName("收货地址");
            endNode.setNodeAddress(route.getEndAddress());
            endNode.setLatitude(route.getEndLatitude());
            endNode.setLongitude(route.getEndLongitude());
            endNode.setSequenceNo(99);
            endNode.setNodeStatus(0);
            logisticsNodeMapper.insert(endNode);
        } else {
            createDefaultNodes(route);
        }
    }

    /** 创建路线时自动插入出发节点和目的地节点 */
    private void createDefaultNodes(LogisticsRoute route) {
        LogisticsNode startNode = new LogisticsNode();
        startNode.setRouteId(route.getId());
        startNode.setNodeType(0);
        startNode.setNodeName("出发仓库");
        startNode.setNodeAddress(route.getStartAddress());
        startNode.setLatitude(route.getStartLatitude());
        startNode.setLongitude(route.getStartLongitude());
        startNode.setSequenceNo(0);
        startNode.setNodeStatus(0);
        logisticsNodeMapper.insert(startNode);

        LogisticsNode endNode = new LogisticsNode();
        endNode.setRouteId(route.getId());
        endNode.setNodeType(2);
        endNode.setNodeName("收货地址");
        endNode.setNodeAddress(route.getEndAddress());
        endNode.setLatitude(route.getEndLatitude());
        endNode.setLongitude(route.getEndLongitude());
        endNode.setSequenceNo(99);
        endNode.setNodeStatus(0);
        logisticsNodeMapper.insert(endNode);
    }

    /** 路线状态转中文描述 */
    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待出发";
            case 1 -> "运输中";
            case 2 -> "已送达";
            case 3 -> "运输异常";
            default -> "未知";
        };
    }

    // ----------------------------------------------------------------
    //  订单全程物流追踪
    // ----------------------------------------------------------------

    @Override
    public List<RouteDetailDTO> getOrderJourney(Long orderId) {
        // Step1：与 order_id 直接关联的所有路线（干线 segmentType=3、旧体系直送等）
        List<LogisticsRoute> directRoutes = logisticsRouteMapper.selectList(
                new LambdaQueryWrapper<LogisticsRoute>()
                        .eq(LogisticsRoute::getOrderId, orderId)
                        .orderByAsc(LogisticsRoute::getCreateTime));

        java.util.Set<Long> addedIds = new java.util.HashSet<>();
        List<RouteDetailDTO> result = new ArrayList<>();
        for (LogisticsRoute r : directRoutes) {
            result.add(buildRouteDetailDTO(r));
            addedIds.add(r.getId());
        }

        // Step2：通过 logistics_batch_item 关联的末端配送路线（多停靠共享路线，order_id 通常为 null）
        LogisticsBatchItem item = batchItemMapper.selectByOrderId(orderId);
        if (item != null && item.getRouteId() != null) {
            // Step2a：若该批次存在 segmentType=1 的干线路线（全国Hub→本地分拨Hub），
            //         补入旅程，让顾客/商家/管理员能看到完整的城市内转运过程。
            //         该路线 orderId=null（批次级），不会被 Step1 捕获。
            if (item.getBatchId() != null) {
                List<LogisticsRoute> batchTrunks = logisticsRouteMapper.selectList(
                        new LambdaQueryWrapper<LogisticsRoute>()
                                .eq(LogisticsRoute::getBatchId, item.getBatchId())
                                .eq(LogisticsRoute::getSegmentType, 1)
                                .orderByAsc(LogisticsRoute::getCreateTime));
                for (LogisticsRoute trunk : batchTrunks) {
                    if (!addedIds.contains(trunk.getId())) {
                        result.add(buildRouteDetailDTO(trunk));
                        addedIds.add(trunk.getId());
                    }
                }
            }

            // Step2b：末端配送路线本体
            if (!addedIds.contains(item.getRouteId())) {
                LogisticsRoute localRoute = logisticsRouteMapper.selectById(item.getRouteId());
                if (localRoute != null) {
                    RouteDetailDTO dto = buildRouteDetailDTO(localRoute);
                    if (localRoute.getStopCount() != null && localRoute.getStopCount() > 1) {
                        dto.setStopSequence(item.getStopSequence());
                        dto.setTotalStops(localRoute.getStopCount());
                        dto.setOrderEndAddress(item.getEndAddress());
                        dto.setOrderEndLat(item.getEndLat());
                        dto.setOrderEndLng(item.getEndLng());
                    }
                    result.add(dto);
                    addedIds.add(item.getRouteId());
                }
            }
        }

        // 按创建时间升序（干线在前，末端在后）
        result.sort(java.util.Comparator.comparing(
                dto -> dto.getRoute() != null ? dto.getRoute().getCreateTime() : new Date(0)));

        return result;
    }

    /** 生成路线编号：LR + yyyyMMddHHmmss + 4位随机数 */
    private String generateRouteNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 10000);
        return "LR" + time + String.format("%04d", rand);
    }
}
