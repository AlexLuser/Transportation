package com.fm.logistics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.common.result.Result;
import com.fm.logistics.dto.BatchAdviceDTO;
import com.fm.logistics.dto.BatchDetailDTO;
import com.fm.logistics.dto.CreateBatchRequestDTO;
import com.fm.logistics.entity.LogisticsBatch;
import com.fm.logistics.entity.LogisticsBatchItem;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.mapper.LogisticsBatchItemMapper;
import com.fm.logistics.mapper.LogisticsBatchMapper;
import com.fm.logistics.service.BatchPlanningAdviceService;
import com.fm.logistics.service.LogisticsBatchService;
import com.fm.logistics.service.LogisticsRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 配送批次管理 Controller
 *
 * POST /api/logistics/batches              - 创建批次（含VRP规划+路线创建）
 * GET  /api/logistics/batches/{id}         - 获取批次详情
 * GET  /api/logistics/batches/{id}/routes  - 获取批次下所有路线段
 */
@Tag(name = "配送批次管理", description = "多订单合并配送、VRP规划、Hub-and-Spoke批次管理接口")
@RestController
@RequestMapping("/api/logistics/batches")
public class LogisticsBatchController {

    @Autowired
    private LogisticsBatchService batchService;

    @Autowired
    private LogisticsRouteService routeService;

    @Autowired
    private LogisticsBatchMapper batchMapper;

    @Autowired
    private BatchPlanningAdviceService batchAdviceService;

    @Autowired
    private LogisticsBatchItemMapper batchItemMapper;

    @Operation(summary = "LLM批次配送策略顾问（方案A）",
               description = "在创建批次前调用，LLM分析订单分布和里程对比，建议是否启用Hub模式及配送策略")
    @PostMapping("/advise")
    public Result<BatchAdviceDTO> advise(@RequestBody CreateBatchRequestDTO requestDTO) {
        if (CollectionUtils.isEmpty(requestDTO.getOrderItems())) {
            return Result.error("订单目的地信息（orderItems）不能为空");
        }
        if (requestDTO.getWarehouseLat() == null || requestDTO.getWarehouseLng() == null) {
            return Result.error("仓库坐标为必填项");
        }
        BatchAdviceDTO advice = batchAdviceService.advise(
                requestDTO.getWarehouseLat(),
                requestDTO.getWarehouseLng(),
                requestDTO.getWarehouseAddress(),
                requestDTO.getOrderItems(),
                requestDTO.getPlannedShipTime()
        );
        return Result.success(advice);
    }

    @Operation(summary = "创建配送批次（含VRP规划）",
               description = "传入多个订单ID，自动执行VRP最优排序、选Hub、创建干线+末端路线")
    @PostMapping
    public Result<BatchDetailDTO> createBatch(@RequestBody CreateBatchRequestDTO requestDTO) {
        if (CollectionUtils.isEmpty(requestDTO.getOrderIds())) {
            return Result.error("订单列表不能为空");
        }
        if (requestDTO.getOrderIds().size() < 2) {
            return Result.error("批次合并至少需要2个订单");
        }
        if (requestDTO.getWarehouseId() == null
                || requestDTO.getWarehouseLat() == null
                || requestDTO.getWarehouseLng() == null) {
            return Result.error("仓库ID和坐标为必填项");
        }
        if (CollectionUtils.isEmpty(requestDTO.getOrderItems())
                || requestDTO.getOrderItems().size() != requestDTO.getOrderIds().size()) {
            return Result.error("订单目的地信息（orderItems）数量必须与orderIds一致");
        }
        if (requestDTO.getUseHub() == null) {
            requestDTO.setUseHub(true);
        }
        BatchDetailDTO result = batchService.createBatch(requestDTO);
        return Result.success(result);
    }

    @Operation(summary = "获取批次详情")
    @GetMapping("/{id}")
    public Result<BatchDetailDTO> getBatchDetail(@PathVariable Long id) {
        return Result.success(batchService.getBatchDetail(id));
    }

    @Operation(summary = "获取批次列表")
    @GetMapping
    public Result<List<LogisticsBatch>> listBatches(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Integer batchStatus) {
        LambdaQueryWrapper<LogisticsBatch> wrapper = new LambdaQueryWrapper<LogisticsBatch>()
                .orderByDesc(LogisticsBatch::getCreateTime);
        if (warehouseId != null) wrapper.eq(LogisticsBatch::getWarehouseId, warehouseId);
        if (batchStatus != null) wrapper.eq(LogisticsBatch::getBatchStatus, batchStatus);
        return Result.success(batchMapper.selectList(wrapper));
    }

    @Operation(summary = "获取批次下所有路线段（干线+末端）")
    @GetMapping("/{id}/routes")
    public Result<List<LogisticsRoute>> getBatchRoutes(@PathVariable Long id) {
        return Result.success(routeService.getRoutesByBatchId(id));
    }

    /**
     * 获取末端路线下所有停靠点（含每站送达状态），供司机端逐站确认送达使用
     * GET /api/logistics/routes/{routeId}/stops
     */
    @Operation(summary = "获取末端路线停靠点列表（含送达状态）")
    @GetMapping("/routes/{routeId}/stops")
    public Result<List<LogisticsBatchItem>> getRouteStops(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @PathVariable Long routeId) {
        List<LogisticsBatchItem> items = batchItemMapper.selectByRouteId(routeId);
        return Result.success(items);
    }

    /**
     * 标记某停靠点（某订单）已送达（item_status = 2），并返回该路线是否全部完成
     * PUT /api/logistics/routes/{routeId}/stops/{orderId}/complete
     * 返回：{"allDone": true/false}
     */
    @Operation(summary = "标记某停靠点已送达")
    @PutMapping("/routes/{routeId}/stops/{orderId}/complete")
    public Result<java.util.Map<String, Object>> completeStop(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @PathVariable Long routeId,
            @PathVariable Long orderId) {
        batchItemMapper.update(null,
                new LambdaUpdateWrapper<LogisticsBatchItem>()
                        .eq(LogisticsBatchItem::getRouteId, routeId)
                        .eq(LogisticsBatchItem::getOrderId, orderId)
                        .set(LogisticsBatchItem::getItemStatus, 2)
                        .set(LogisticsBatchItem::getUpdateTime, new Date()));
        boolean allDone = batchItemMapper.countPendingByRouteId(routeId) == 0;
        return Result.success(java.util.Map.of("allDone", allDone));
    }
}
