package com.fm.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.logistics.dto.InterCityBatchDTO;
import com.fm.logistics.dto.InterCityBatchOrderLineDTO;
import com.fm.logistics.entity.*;
import com.fm.logistics.mapper.*;
import com.fm.logistics.service.InterCityBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterCityBatchServiceImpl implements InterCityBatchService {

    private final InterCityBatchMapper interCityBatchMapper;
    private final NationalHubMapper nationalHubMapper;
    private final LogisticsRouteMapper logisticsRouteMapper;
    private final DispatchPoolMapper dispatchPoolMapper;

    // 用于查询批次内订单 & 更新订单状态
    private final OrderInfoMapper orderInfoMapper;

    @Override
    public List<InterCityBatchDTO> listActiveBatches() {
        List<InterCityBatch> batches = interCityBatchMapper.selectActiveBatches();
        Map<Long, NationalHub> hubMap = buildHubMap();
        return batches.stream()
                .map(b -> toDTO(b, hubMap))
                .peek(dto -> attachOrdersInBatch(dto, hubMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void onDeparture(Long batchId) {
        InterCityBatch batch = interCityBatchMapper.selectById(batchId);
        if (batch == null) throw new IllegalArgumentException("干线批次不存在: " + batchId);
        if (!"CREATED".equals(batch.getStatus()))
            throw new IllegalStateException("批次状态不是CREATED: " + batch.getStatus());

        // 更新批次状态
        batch.setStatus("DEPARTED");
        batch.setActualDepart(new Date());
        interCityBatchMapper.updateById(batch);

        // 为批次内订单创建 segmentType=3 虚拟路线
        createTrunkVirtualRoutes(batch);

        // 更新相关订单状态为 3（派送中）
        updateOrderStatusInBatch(batchId, 3);

        log.info("[InterCityBatch] 批次{}已发车，创建虚拟路线完成", batchId);
    }

    @Override
    @Transactional
    public void onArrival(Long batchId) {
        InterCityBatch batch = interCityBatchMapper.selectById(batchId);
        if (batch == null) throw new IllegalArgumentException("干线批次不存在: " + batchId);
        if (!"DEPARTED".equals(batch.getStatus()))
            throw new IllegalStateException("批次状态不是DEPARTED: " + batch.getStatus());

        // 更新批次状态
        batch.setStatus("ARRIVED");
        batch.setActualArrive(new Date());
        interCityBatchMapper.updateById(batch);

        // 将 segmentType=3 虚拟路线标记为完成
        interCityBatchMapper.updateRouteStatusByBatchId(batchId, 2);

        // 获取目标 Hub 信息
        NationalHub destHub = nationalHubMapper.selectById(batch.getToHubId());

        // 区分：dest_hub_id == toHubId 的为最终目的地订单；其余为需继续中转的订单
        List<Long> allOrderIds = getOrderIdsByBatchId(batchId);
        List<Long> finalOrderIds = orderInfoMapper.selectOrderIdsByBatchAndFinalDestHub(batchId, batch.getToHubId());
        Set<Long> finalSet = new HashSet<>(finalOrderIds);
        List<Long> transitOrderIds = allOrderIds.stream()
                .filter(id -> !finalSet.contains(id))
                .collect(Collectors.toList());

        // ── 最终目的地订单：写入 / 更新 dispatch_pool，进入城市末端调度 ──
        for (Long orderId : finalOrderIds) {
            // 使用 selectList+LIMIT 1 防止重复测试数据导致 TooManyResultsException
            List<DispatchPool> existingList = dispatchPoolMapper.selectList(
                    new LambdaQueryWrapper<DispatchPool>()
                            .eq(DispatchPool::getOrderId, orderId)
                            .orderByDesc(DispatchPool::getId)
                            .last("LIMIT 1"));
            DispatchPool existing = existingList.isEmpty() ? null : existingList.get(0);
            if (existing != null) {
                // 无论之前状态如何，重新激活为「待调度」，并更新为干线到达起点信息
                existing.setDispatchOriginType(1);
                existing.setDispatchOriginLat(destHub != null ? destHub.getLatitude() : null);
                existing.setDispatchOriginLng(destHub != null ? destHub.getLongitude() : null);
                existing.setDispatchOriginAddr(destHub != null ? destHub.getName() + "（干线到达）" : "");
                existing.setIsCrossCity(1);
                existing.setDestHubId(batch.getToHubId());
                existing.setStatus(0);          // 重置为待调度
                existing.setBatchId(null);       // 清除旧批次关联
                existing.setEnterTime(LocalDateTime.now());
                dispatchPoolMapper.updateById(existing);
            } else {
                // 调度池中无该订单记录（跨城新单首次到达），补建一条
                DispatchPool pool = buildDispatchPoolFromOrderRow(orderId, destHub, batch.getToHubId());
                if (pool != null) {
                    dispatchPoolMapper.insert(pool);
                } else {
                    log.warn("[InterCityBatch] orderId={} 无法构建 dispatch_pool 记录，跳过", orderId);
                }
            }
        }

        // ── 中转订单：优先根据 planned_path 自动衔接下一跳预规划批次 ──
        if (!transitOrderIds.isEmpty()) {
            autoChainTransitOrders(batch, transitOrderIds);
        }

        log.info("[InterCityBatch] 批次{}已到达：最终目的地{}单入末端调度池，中转{}单自动衔接/重置",
                batchId, finalOrderIds.size(), transitOrderIds.size());
    }

    // ── 私有辅助方法 ─────────────────────────────────────────────────

    private void createTrunkVirtualRoutes(InterCityBatch batch) {
        NationalHub from = nationalHubMapper.selectById(batch.getFromHubId());
        NationalHub to   = nationalHubMapper.selectById(batch.getToHubId());
        if (from == null || to == null) return;

        // Hub→Hub 直线 GeoJSON
        String plannedRoute = String.format(
                "{\"type\":\"LineString\",\"coordinates\":[[%f,%f],[%f,%f]]}",
                from.getLongitude(), from.getLatitude(),
                to.getLongitude(), to.getLatitude());

        List<Long> orderIds = getOrderIdsByBatchId(batch.getId());
        String routeNoPrefix = "IB" + batch.getId() + "_";
        int seq = 0;
        for (Long orderId : orderIds) {
            LogisticsRoute route = new LogisticsRoute();
            route.setRouteNo(routeNoPrefix + (++seq));
            route.setOrderId(orderId);
            route.setSegmentType(3);
            route.setInterCityBatchId(batch.getId());
            route.setStartAddress(from.getName());
            route.setStartLatitude(from.getLatitude());
            route.setStartLongitude(from.getLongitude());
            route.setEndAddress(to.getName());
            route.setEndLatitude(to.getLatitude());
            route.setEndLongitude(to.getLongitude());
            route.setPlannedRoute(plannedRoute);
            route.setRouteStatus(1); // 运输中（直接激活）
            logisticsRouteMapper.insert(route);
        }
    }

    /** 从 order_info 查询归属该批次的订单ID列表 */
    private List<Long> getOrderIdsByBatchId(Long batchId) {
        return orderInfoMapper.selectOrderIdsByInterCityBatchId(batchId);
    }

    private DispatchPool buildDispatchPoolFromOrderRow(Long orderId, NationalHub destHub, Long batchToHubId) {
        Map<String, Object> row = orderInfoMapper.selectOrderRowForDispatchPool(orderId);
        if (row == null || row.get("orderId") == null) {
            log.warn("[InterCityBatch] 无法为 orderId={} 补建 dispatch_pool：无订单/地址数据", orderId);
            return null;
        }
        DispatchPool pool = new DispatchPool();
        pool.setOrderId(longOf(row.get("orderId")));
        pool.setShopId(longOf(row.get("shopId")));
        pool.setWarehouseId(longOf(row.get("warehouseId")));
        pool.setEndAddress(strOf(row.get("endAddress")));
        pool.setEndLat(doubleOf(row.get("endLat")));
        pool.setEndLng(doubleOf(row.get("endLng")));
        pool.setReceiverName(strOf(row.get("receiverName")));
        pool.setReceiverPhone(strOf(row.get("receiverPhone")));
        pool.setRemark(strOf(row.get("remark")));
        pool.setOriginHubId(longOf(row.get("originHubId")));
        Long dHub = longOf(row.get("destHubId"));
        pool.setDestHubId(dHub != null ? dHub : batchToHubId);
        pool.setIsCrossCity(1);
        pool.setDispatchOriginType(1);
        pool.setDispatchOriginLat(destHub != null ? destHub.getLatitude() : null);
        pool.setDispatchOriginLng(destHub != null ? destHub.getLongitude() : null);
        pool.setDispatchOriginAddr(destHub != null ? destHub.getName() + "（干线到达）" : "");
        pool.setStatus(0);
        pool.setEnterTime(LocalDateTime.now());
        return pool;
    }

    private static Long longOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.parseLong(o.toString());
    }

    private static String strOf(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Double doubleOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString());
    }

    /**
     * 中转订单自动衔接下一跳批次。
     *
     * <p>修复要点：先按「下一跳 Hub」分组归集所有订单，再每组只查询一次预规划批次并批量绑单后一次性激活。
     * 避免原逐单循环中「首单激活后批次变为 CREATED，后续订单调 selectChainedBatch(CHAINED) 返回 null」的问题。</p>
     */
    private void autoChainTransitOrders(InterCityBatch arrivedBatch, List<Long> transitOrderIds) {
        Long currentHubId = arrivedBatch.getToHubId();
        Long planId       = arrivedBatch.getFlowPlanId();

        // 第一步：解析每个订单的下一跳 Hub，按 nextHubId 分组
        Map<Long, List<Long>> ordersByNextHub = new LinkedHashMap<>();
        List<Long> fallbackIds = new ArrayList<>();

        for (Long orderId : transitOrderIds) {
            String pathJson = orderInfoMapper.selectPlannedPathById(orderId);
            List<Long> path = parsePlannedPath(pathJson);
            int idx = path.indexOf(currentHubId);

            if (idx >= 0 && idx < path.size() - 1 && planId != null) {
                Long nextHubId = path.get(idx + 1);
                ordersByNextHub.computeIfAbsent(nextHubId, k -> new ArrayList<>()).add(orderId);
            } else {
                if (path.isEmpty()) {
                    log.warn("[InterCityBatch] 订单{} 无 planned_path，降级重置", orderId);
                } else {
                    log.warn("[InterCityBatch] 订单{} 在路径中找不到当前 Hub{}，降级重置", orderId, currentHubId);
                }
                fallbackIds.add(orderId);
            }
        }

        // 第二步：每个下一跳分组只查一次批次，批量绑单后统一激活
        int chained = 0;
        for (Map.Entry<Long, List<Long>> entry : ordersByNextHub.entrySet()) {
            Long nextHubId      = entry.getKey();
            List<Long> orderIds = entry.getValue();

            InterCityBatch nextBatch = interCityBatchMapper.selectChainedBatch(currentHubId, nextHubId, planId);
            if (nextBatch == null) {
                log.warn("[InterCityBatch] {}→{} 无预规划批次(planId={})，{}单降级重置",
                        currentHubId, nextHubId, planId, orderIds.size());
                fallbackIds.addAll(orderIds);
                continue;
            }

            // 批量绑单（一次 SQL，不触发批次状态变更）
            int updated = orderInfoMapper.assignOrdersToNextBatch(orderIds, nextBatch.getId(), currentHubId);
            // 激活批次（CHAINED → CREATED），更新实单数
            int newCount = orderInfoMapper.countByInterCityBatchId(nextBatch.getId());
            nextBatch.setStatus("CREATED");
            nextBatch.setItemCount(newCount);
            interCityBatchMapper.updateById(nextBatch);

            log.info("[InterCityBatch] {}→{} 批次{} 激活，绑单{}笔（写入{}行）",
                    currentHubId, nextHubId, nextBatch.getId(), orderIds.size(), updated);
            chained += updated;
        }

        // 第三步：降级订单重置等待下次 MCMF 规划
        if (!fallbackIds.isEmpty()) {
            int reset = orderInfoMapper.resetTransitOrdersAfterHop(fallbackIds, currentHubId);
            log.info("[InterCityBatch] 批次{}: 衔接{}单，降级重置{}单（实际重置{}行）",
                    arrivedBatch.getId(), chained, fallbackIds.size(), reset);
        } else {
            log.info("[InterCityBatch] 批次{}: 全部{}单已自动衔接下一跳批次",
                    arrivedBatch.getId(), chained);
        }
    }

    /** 将 JSON 数组字符串 "[1,3,7]" 解析为 Long 列表 */
    private List<Long> parsePlannedPath(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            String trimmed = json.trim();
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return Collections.emptyList();
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) return Collections.emptyList();
            List<Long> result = new ArrayList<>();
            for (String part : inner.split(",")) {
                result.add(Long.parseLong(part.trim()));
            }
            return result;
        } catch (Exception e) {
            log.warn("[InterCityBatch] 解析 planned_path 失败: {}", json);
            return Collections.emptyList();
        }
    }

    /** 更新批次内订单状态 */
    private void updateOrderStatusInBatch(Long batchId, int status) {
        orderInfoMapper.updateStatusByInterCityBatchId(batchId, status);
    }

    private Map<Long, NationalHub> buildHubMap() {
        List<NationalHub> hubs = nationalHubMapper.selectAllActive();
        Map<Long, NationalHub> map = new HashMap<>();
        hubs.forEach(h -> map.put(h.getId(), h));
        return map;
    }

    private InterCityBatchDTO toDTO(InterCityBatch batch, Map<Long, NationalHub> hubMap) {
        InterCityBatchDTO dto = new InterCityBatchDTO();
        dto.setId(batch.getId());
        dto.setBatchNo(batch.getBatchNo());
        dto.setFlowPlanId(batch.getFlowPlanId());
        dto.setFromHubId(batch.getFromHubId());
        dto.setToHubId(batch.getToHubId());
        dto.setTransportMode(batch.getTransportMode());
        dto.setPlannedDepart(batch.getPlannedDepart());
        dto.setActualDepart(batch.getActualDepart());
        dto.setActualArrive(batch.getActualArrive());
        dto.setStatus(batch.getStatus());
        dto.setItemCount(batch.getItemCount());
        dto.setRemark(batch.getRemark());
        NationalHub from = hubMap.get(batch.getFromHubId());
        NationalHub to   = hubMap.get(batch.getToHubId());
        if (from != null) { dto.setFromHubName(from.getName()); dto.setFromCity(from.getCity()); }
        if (to != null)   { dto.setToHubName(to.getName()); dto.setToCity(to.getCity()); }
        return dto;
    }

    /** 填充本批绑定的实单；发车/到达仅影响这些行对应的订单。 */
    private void attachOrdersInBatch(InterCityBatchDTO dto, Map<Long, NationalHub> hubMap) {
        List<Map<String, Object>> rows = orderInfoMapper.selectOrderRowsByInterCityBatchId(dto.getId());
        if (rows == null || rows.isEmpty()) {
            dto.setOrders(Collections.emptyList());
            return;
        }
        List<InterCityBatchOrderLineDTO> lines = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object idObj = row.get("id");
            if (idObj == null) {
                continue;
            }
            InterCityBatchOrderLineDTO line = new InterCityBatchOrderLineDTO();
            line.setOrderId(((Number) idObj).longValue());
            Object on = row.get("orderNo");
            if (on == null) {
                on = row.get("orderno");
            }
            line.setOrderNo(on != null ? on.toString() : null);
            Object st = row.get("orderStatus");
            if (st == null) {
                st = row.get("orderstatus");
            }
            line.setOrderStatus(st != null ? ((Number) st).intValue() : null);
            Object oho = row.get("originHubId");
            if (oho == null) {
                oho = row.get("originhubid");
            }
            Object dho = row.get("destHubId");
            if (dho == null) {
                dho = row.get("desthubid");
            }
            Long oh = oho != null ? ((Number) oho).longValue() : null;
            Long dh = dho != null ? ((Number) dho).longValue() : null;
            NationalHub o = oh != null ? hubMap.get(oh) : null;
            NationalHub d = dh != null ? hubMap.get(dh) : null;
            line.setOriginHubName(o != null ? o.getName() : "—");
            line.setDestHubName(d != null ? d.getName() : "—");
            lines.add(line);
        }
        dto.setOrders(lines);
    }
}
