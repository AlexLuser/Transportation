package com.fm.logistics.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.common.result.Result;
import com.fm.logistics.dto.*;
import com.fm.logistics.entity.DispatchPool;
import com.fm.logistics.mapper.DispatchPoolMapper;
import com.fm.logistics.mapper.LogisticsRouteMapper;
import com.fm.logistics.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能调度控制器
 *
 * 提供调度池查看、聚类预览、LLM 建议、执行调度、司机附近路线段等接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/logistics")
public class DispatchController {

    @Autowired
    private DispatchPoolMapper dispatchPoolMapper;

    @Autowired
    private LogisticsRouteMapper logisticsRouteMapper;

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private GlobalDispatchAdviceService globalDispatchAdviceService;

    @Autowired
    private LogisticsBatchService logisticsBatchService;

    @Autowired
    private FlowPlanService flowPlanService;

    @Autowired
    private InterCityBatchService interCityBatchService;

    // ================================================================
    //  全国干线调度（MCMF）
    // ================================================================

    /**
     * Tab1：手动触发 MCMF 规划，返回流量分配预览（同步，结果落库）
     */
    @PostMapping("/dispatch/national/plan")
    public Result<FlowPlanDetailDTO> triggerNationalPlan(
            @RequestParam(required = false) String planDate,
            @RequestParam(required = false) String llmReferenceDate) {
        LocalDate date = (planDate != null && !planDate.isBlank())
                ? LocalDate.parse(planDate) : LocalDate.now();
        LocalDate llmRef = (llmReferenceDate != null && !llmReferenceDate.isBlank())
                ? LocalDate.parse(llmReferenceDate) : null;
        return Result.success(flowPlanService.triggerManually(date, llmRef));
    }

    /**
     * Tab1：查询当日干线批次列表（CREATED + DEPARTED 状态）
     */
    @GetMapping("/dispatch/national/batches")
    public Result<List<InterCityBatchDTO>> getNationalBatches() {
        return Result.success(interCityBatchService.listActiveBatches());
    }

    /**
     * Tab1：标记发车（CREATED → DEPARTED）
     */
    @PostMapping("/dispatch/national/{batchId}/depart")
    public Result<Void> departBatch(@PathVariable Long batchId) {
        interCityBatchService.onDeparture(batchId);
        return Result.success(null);
    }

    /**
     * Tab1：标记到达（DEPARTED → ARRIVED），并将订单写入目标城市调度池
     */
    @PostMapping("/dispatch/national/{batchId}/arrive")
    public Result<Void> arriveBatch(@PathVariable Long batchId) {
        interCityBatchService.onArrival(batchId);
        return Result.success(null);
    }

    /**
     * Tab1：查询最新 MCMF 规划结果
     */
    @GetMapping("/dispatch/national/latest-plan")
    public Result<FlowPlanDetailDTO> getLatestPlan() {
        return Result.success(flowPlanService.getLatest());
    }

    // ================================================================
    //  调度池
    // ================================================================

    /**
     * 查询调度池中的待调度订单列表（末端城市配送用）
     * 仅返回同城单 OR 跨城干线已到达（dispatch_origin_type=1）的订单，
     * 不含"跨城揽收待处理"（那类由 /dispatch/collection-queue 单独提供）。
     *
     * @param destHubId 可选，全国网收货 Hub（national_hub.id），与 order_info.dest_hub_id 一致
     */
    @GetMapping("/dispatch/pool")
    public Result<List<DispatchPoolItemDTO>> getDispatchPool(
            @RequestParam(required = false) Long destHubId) {
        List<DispatchPool> list = dispatchPoolMapper.selectAllPending(destHubId);
        List<DispatchPoolItemDTO> dtos = list.stream().map(this::toItemDTO).collect(Collectors.toList());
        return Result.success(dtos);
    }

    /**
     * 查询"揽收待处理"队列（配送中心作业看板专用）
     * 返回跨城订单中商家已发货但尚未进入干线的条目（is_cross_city=1, dispatch_origin_type=0）。
     * 与 /dispatch/pool 互斥：两者覆盖订单生命周期的不同阶段，不存在重叠。
     *
     * @param originHubId 发货所在城市 Hub ID（必填）
     */
    @GetMapping("/dispatch/collection-queue")
    public Result<List<DispatchPoolItemDTO>> getCollectionQueue(
            @RequestParam Long originHubId) {
        List<DispatchPool> list = dispatchPoolMapper.selectCollectionQueue(originHubId);
        List<DispatchPoolItemDTO> dtos = list.stream().map(this::toItemDTO).collect(Collectors.toList());
        return Result.success(dtos);
    }

    // ================================================================
    //  预览（K-Means + LLM 建议）
    // ================================================================

    /**
     * 调度预览：对调度池内的待调度订单执行 K-Means 聚类 + LLM 全局建议
     *
     * @param k           聚类数（可选，null=自动决策）
     * @param warehouseId 用于 LLM Hub 距离判断的主仓库ID（可选）
     * @param orderIds     指定勾选的订单ID（逗号分隔，可选；为空则取全部待调度有坐标订单）
     * @param warehouseLat 仓库纬度（供 LLM 计算仓库→簇距离）
     * @param warehouseLng 仓库经度
     * @param skipLlm      true=跳过 LLM，仅做 K-Means 聚类（直接送达时可跳过）
     */
    @GetMapping("/dispatch/preview")
    public Result<DispatchPreviewDTO> previewDispatch(
            @RequestParam(required = false) Integer k,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String orderIds,
            @RequestParam(required = false) Double warehouseLat,
            @RequestParam(required = false) Double warehouseLng,
            @RequestParam(required = false, defaultValue = "false") boolean skipLlm,
            @RequestParam(required = false) Long destHubId) {

        List<Long> orderIdList = null;
        if (orderIds != null && !orderIds.isBlank()) {
            orderIdList = Arrays.stream(orderIds.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).collect(Collectors.toList());
        }

        List<DispatchPool> pending = (orderIdList != null && !orderIdList.isEmpty())
                ? dispatchPoolMapper.selectPendingByOrderIds(orderIdList, destHubId)
                : dispatchPoolMapper.selectPendingWithCoords(destHubId);

        if (pending.isEmpty()) {
            DispatchPreviewDTO empty = new DispatchPreviewDTO();
            empty.setTotalOrders(0);
            empty.setSuggestedBatchCount(0);
            empty.setClusters(new ArrayList<>());
            return Result.success(empty);
        }

        List<DispatchPoolItemDTO> items = pending.stream().map(this::toItemDTO).collect(Collectors.toList());

        // K-Means 聚类
        List<ClusterResultDTO> clusters = clusterService.cluster(items, k);

        // 若调用方未传起点坐标，从池条目的 dispatchOriginLat/Lng 中推断：
        // 优先取跨城到达（type=1）订单的 Hub 坐标，其次取同城仓库坐标，均为第一个有效值。
        if (warehouseLat == null || warehouseLng == null) {
            DispatchPool representative = pending.stream()
                    .filter(p -> p.getDispatchOriginLat() != null && p.getDispatchOriginLng() != null)
                    .min(java.util.Comparator.comparingInt(
                            p -> p.getDispatchOriginType() == null ? 99 : (1 - p.getDispatchOriginType())))
                    .orElse(null);
            if (representative != null) {
                warehouseLat = representative.getDispatchOriginLat();
                warehouseLng = representative.getDispatchOriginLng();
                log.debug("[Dispatch] 自动推断起点坐标 from pool item orderId={}: ({}, {})",
                        representative.getOrderId(), warehouseLat, warehouseLng);
            }
        }

        // LLM 全局建议（直接送达模式可跳过）
        GlobalDispatchAdviceDTO advice;
        if (skipLlm) {
            advice = buildDirectDeliveryAdvice(clusters);
        } else {
            advice = globalDispatchAdviceService.advise(clusters, warehouseId, warehouseLat, warehouseLng);
        }

        DispatchPreviewDTO preview = new DispatchPreviewDTO();
        preview.setTotalOrders(items.size());
        preview.setSuggestedBatchCount(advice.getSuggestedBatchCount());
        preview.setKUsed(clusters.size());
        preview.setClusters(clusters);
        preview.setLlmAdvice(advice);
        preview.setLlmEnhanced(advice.isLlmEnhanced());

        return Result.success(preview);
    }

    // ================================================================
    //  执行调度
    // ================================================================

    /**
     * 执行调度：根据管理员确认的方案创建批次，并标记调度池内对应订单为已调度
     */
    @PostMapping("/dispatch/execute")
    public Result<List<BatchDetailDTO>> executeDispatch(@RequestBody ExecuteDispatchRequestDTO req) {
        if (req.getBatches() == null || req.getBatches().isEmpty()) {
            return Result.error("请求中没有批次数据");
        }

        List<BatchDetailDTO> results = new ArrayList<>();

        for (ExecuteDispatchRequestDTO.BatchItem item : req.getBatches()) {
            try {
                // 构建 CreateBatchRequestDTO
                CreateBatchRequestDTO batchReq = new CreateBatchRequestDTO();
                batchReq.setOrderIds(item.getOrderIds());
                batchReq.setWarehouseId(item.getWarehouseId());
                batchReq.setWarehouseLat(item.getWarehouseLat());
                batchReq.setWarehouseLng(item.getWarehouseLng());
                batchReq.setWarehouseAddress(item.getWarehouseAddress());
                batchReq.setUseHub(item.getUseHub() != null ? item.getUseHub() : true);
                batchReq.setIsCrossCity(item.getIsCrossCity());
                batchReq.setHubId(item.getHubId());
                batchReq.setPlannedShipTime(item.getPlannedShipTime());

                // 若前端未传 orderItems，从 dispatch_pool 补全
                if (item.getOrderItems() == null || item.getOrderItems().isEmpty()) {
                    List<CreateBatchRequestDTO.OrderItem> orderItems = buildOrderItems(item.getOrderIds());
                    batchReq.setOrderItems(orderItems);
                } else {
                    batchReq.setOrderItems(item.getOrderItems());
                }

                BatchDetailDTO detail = logisticsBatchService.createBatch(batchReq);
                results.add(detail);

                // 标记 dispatch_pool 中对应订单为已调度
                if (detail.getBatch() != null) {
                    Long batchId = detail.getBatch().getId();
                    markDispatched(item.getOrderIds(), batchId);
                }
            } catch (Exception e) {
                log.error("[调度执行] 批次创建失败: orderIds={}, error={}", item.getOrderIds(), e.getMessage());
                return Result.error("批次创建失败: " + e.getMessage());
            }
        }

        return Result.success(results);
    }

    // ================================================================
    //  紧急性检测（调度池加载后立即调用，返回 LLM 判定为急送的 orderId 列表）
    // ================================================================

    /**
     * 对调度池中所有含备注的待调度订单进行 LLM 紧急性判断
     */
    @GetMapping("/dispatch/check-urgency")
    public Result<List<Long>> checkUrgency(@RequestParam(required = false) Long destHubId) {
        List<DispatchPool> pending = dispatchPoolMapper.selectAllPending(destHubId);
        List<DispatchPoolItemDTO> items = pending.stream()
                .map(this::toItemDTO).collect(Collectors.toList());
        List<Long> urgentIds = globalDispatchAdviceService.checkUrgency(items);
        return Result.success(urgentIds);
    }

    // ================================================================
    //  内部工具方法
    // ================================================================

    /** 直接送达模式下跳过 LLM，为每个簇生成默认的直送 advice */
    private GlobalDispatchAdviceDTO buildDirectDeliveryAdvice(List<ClusterResultDTO> clusters) {
        GlobalDispatchAdviceDTO dto = new GlobalDispatchAdviceDTO();
        dto.setSuggestedBatchCount(clusters.size());
        dto.setStrategy("DIRECT");
        dto.setReason("已选择直接送达模式，所有批次不经中转站");
        dto.setLlmEnhanced(false);
        List<GlobalDispatchAdviceDTO.BatchSuggestion> sgs = new ArrayList<>();
        for (ClusterResultDTO c : clusters) {
            GlobalDispatchAdviceDTO.BatchSuggestion bs = new GlobalDispatchAdviceDTO.BatchSuggestion();
            bs.setClusterId(c.getClusterId());
            bs.setUseHub(false);
            bs.setUrgency("MEDIUM");
            bs.setNote("直接送达（已由调度员手动指定）");
            sgs.add(bs);
        }
        dto.setBatchSuggestions(sgs);
        return dto;
    }

    private DispatchPoolItemDTO toItemDTO(DispatchPool pool) {
        DispatchPoolItemDTO dto = new DispatchPoolItemDTO();
        dto.setPoolId(pool.getId());
        dto.setOrderId(pool.getOrderId());
        dto.setShopId(pool.getShopId());
        dto.setWarehouseId(pool.getWarehouseId());
        dto.setEndAddress(pool.getEndAddress());
        dto.setEndLat(pool.getEndLat());
        dto.setEndLng(pool.getEndLng());
        dto.setReceiverName(pool.getReceiverName());
        dto.setReceiverPhone(pool.getReceiverPhone());
        dto.setRemark(pool.getRemark());
        dto.setEnterTime(pool.getEnterTime());
        dto.setStatus(pool.getStatus());
        // MCMF 跨城扩展字段
        dto.setOriginHubId(pool.getOriginHubId());
        dto.setDestHubId(pool.getDestHubId());
        dto.setIsCrossCity(pool.getIsCrossCity());
        dto.setDispatchOriginType(pool.getDispatchOriginType());
        dto.setDispatchOriginLat(pool.getDispatchOriginLat());
        dto.setDispatchOriginLng(pool.getDispatchOriginLng());
        dto.setDispatchOriginAddr(pool.getDispatchOriginAddr());
        return dto;
    }

    private List<CreateBatchRequestDTO.OrderItem> buildOrderItems(List<Long> orderIds) {
        if (orderIds == null) return new ArrayList<>();
        List<DispatchPool> pools = dispatchPoolMapper.selectBatchIds(orderIds);
        Map<Long, DispatchPool> poolMap = pools.stream()
                .collect(Collectors.toMap(DispatchPool::getOrderId, p -> p));
        List<CreateBatchRequestDTO.OrderItem> items = new ArrayList<>();
        for (Long oid : orderIds) {
            CreateBatchRequestDTO.OrderItem oi = new CreateBatchRequestDTO.OrderItem();
            oi.setOrderId(oid);
            DispatchPool p = poolMap.get(oid);
            if (p != null) {
                oi.setEndLat(p.getEndLat());
                oi.setEndLng(p.getEndLng());
                oi.setEndAddress(p.getEndAddress());
                oi.setReceiverName(p.getReceiverName());
                oi.setReceiverPhone(p.getReceiverPhone());
            }
            items.add(oi);
        }
        return items;
    }

    private void markDispatched(List<Long> orderIds, Long batchId) {
        if (orderIds == null || orderIds.isEmpty()) return;
        LambdaUpdateWrapper<DispatchPool> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(DispatchPool::getOrderId, orderIds)
               .set(DispatchPool::getStatus, 1)
               .set(DispatchPool::getBatchId, batchId)
               .set(DispatchPool::getDispatchTime, LocalDateTime.now());
        dispatchPoolMapper.update(null, wrapper);
    }
}
