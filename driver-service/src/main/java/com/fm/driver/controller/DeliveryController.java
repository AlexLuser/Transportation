package com.fm.driver.controller;

import com.fm.common.dto.PageResult;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.driver.entity.Driver;
import com.fm.driver.entity.OrderDelivery;
import com.fm.driver.feign.LogisticsFeignClient;
import com.fm.driver.feign.OrderFeignClient;
import com.fm.driver.service.DeliveryService;
import com.fm.driver.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 配送订单管理Controller
 */
@Slf4j
@Tag(name = "配送管理", description = "订单配送相关接口")
@RestController
@RequestMapping("/api/drivers/deliveries")
public class DeliveryController {
    
    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private LogisticsFeignClient logisticsFeign;

    @Autowired
    private OrderFeignClient orderFeign;
    
    @Operation(summary = "进行中的配送", description = "当前司机已接单或运输中的配送；计划路线请调 logistics-service：GET /api/logistics/routes/order/{orderId}")
    @GetMapping("/in-progress")
    public Result<List<OrderDelivery>> getInProgressDeliveries(
            @RequestHeader(value = "userId", required = false) String userIdHeader) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }
        return Result.success(deliveryService.listInProgressDeliveries(driver.getId()));
    }

    @Operation(summary = "获取待接单订单列表", description = "获取待接单的订单列表（分页）")
    @GetMapping("/pending")
    public Result<PageResult<OrderDelivery>> getPendingDeliveries(
            @Parameter(description = "当前页码（从1开始）")
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @Parameter(description = "每页大小")
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        PageResult<OrderDelivery> pageResult = deliveryService.getPendingDeliveries(current, size);
        return Result.success(pageResult);
    }
    
    /**
     * 获取我的配送订单列表（分页）
     */
    @Operation(summary = "获取我的配送订单列表", description = "获取当前运输员的配送订单列表（分页，支持状态筛选和排序）")
    @GetMapping
    public Result<PageResult<OrderDelivery>> getMyDeliveries(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "当前页码（从1开始）")
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @Parameter(description = "每页大小")
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @Parameter(description = "配送状态（可选：0-待接单，1-已接单，2-运输中，3-已送达，4-已取消）")
            @RequestParam(value = "status", required = false) Integer status,
            @Parameter(description = "排序字段（可选：createTime, acceptTime, deliveryTime）")
            @RequestParam(value = "sortField", required = false) String sortField,
            @Parameter(description = "排序方向（可选：asc, desc）")
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }
        
        PageResult<OrderDelivery> pageResult = deliveryService.getMyDeliveries(
            driver.getId(), current, size, status, sortField, sortOrder);
        return Result.success(pageResult);
    }
    
    /**
     * 获取配送详情
     */
    @Operation(summary = "获取配送详情", description = "根据配送ID获取配送详情")
    @GetMapping("/{id}")
    public Result<OrderDelivery> getDelivery(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "配送ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        OrderDelivery delivery = deliveryService.getDeliveryById(id);
        if (delivery == null) {
            return Result.error("配送记录不存在");
        }
        
        // 验证权限：只能查看自己的配送记录或待接单的订单
        if (delivery.getDriverId() != null) {
            Driver driver = driverService.getDriverByUserId(userId);
            if (driver == null || !delivery.getDriverId().equals(driver.getId())) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        }
        
        return Result.success(delivery);
    }
    
    /**
     * 接单
     */
    @Operation(summary = "接单", description = "接受配送订单")
    @PostMapping("/{id}/accept")
    public Result<OrderDelivery> acceptDelivery(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "配送ID", required = true)
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> requestBody) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }
        
        Long vehicleId = null;
        if (requestBody != null && requestBody.containsKey("vehicleId")) {
            vehicleId = Long.valueOf(requestBody.get("vehicleId").toString());
        }
        
        OrderDelivery delivery = deliveryService.acceptDelivery(id, driver.getId(), vehicleId);

        // 多停靠末端路线（orderId=null，segmentType=2）：接单后逐单通知 order-service → 派送中
        if (delivery.getOrderId() == null
                && Integer.valueOf(2).equals(delivery.getSegmentType())
                && delivery.getRouteId() != null) {
            updateMultiStopOrdersStatus(delivery.getRouteId(), 3, userIdHeader);
        }

        return Result.success(delivery);
    }
    
    /**
     * 更新配送状态
     */
    @Operation(summary = "更新配送状态", description = "更新配送状态（取货/运输中/已送达）")
    @PutMapping("/{id}/status")
    public Result<OrderDelivery> updateDeliveryStatus(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "配送ID", required = true)
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestBody) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }
        
        Integer status = Integer.valueOf(requestBody.get("status").toString());
        String remark = requestBody.containsKey("remark") ? requestBody.get("remark").toString() : null;

        // 先取出 delivery 信息（在 service 更新之前），用于判断多停靠路线
        OrderDelivery deliveryBefore = deliveryService.getDeliveryById(id);

        OrderDelivery delivery = deliveryService.updateDeliveryStatus(id, driver.getId(), status, remark);

        // 若是"确认送达"且为多停靠末端路线（orderId=null, segmentType=2, routeId 非空），
        // 自动补全所有尚未完成的停靠点，避免 LogisticsBatchItem 停留在"待配送"
        if (status == 3
                && deliveryBefore != null
                && deliveryBefore.getRouteId() != null
                && Integer.valueOf(2).equals(deliveryBefore.getSegmentType())
                && deliveryBefore.getOrderId() == null) {
            try {
                Long routeId = deliveryBefore.getRouteId();
                Result<List<Map<String, Object>>> stopsResult = logisticsFeign.getRouteStops(routeId, userIdHeader);
                if (stopsResult != null && stopsResult.getData() != null) {
                    for (Map<String, Object> stop : stopsResult.getData()) {
                        Object itemStatus = stop.get("itemStatus");
                        Object stopOrderId = stop.get("orderId");
                        // itemStatus != 2（未送达）且 orderId 非空 → 自动标记
                        if (stopOrderId != null && !Integer.valueOf(2).equals(itemStatus)) {
                            Long stopOid = Long.parseLong(stopOrderId.toString());
                            logisticsFeign.completeStop(routeId, stopOid, userIdHeader);
                            try {
                                orderFeign.updateOrderStatus(stopOid, java.util.Map.of("orderStatus", 6), userIdHeader, "admin");
                            } catch (Exception ignored) { /* 非致命 */ }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[updateDeliveryStatus] 多停靠路线自动补全停靠点失败: {}", e.getMessage());
            }
        }

        return Result.success(delivery);
    }
    
    /**
     * 取消配送
     */
    @Operation(summary = "取消配送", description = "取消配送订单")
    @PostMapping("/{id}/cancel")
    public Result<OrderDelivery> cancelDelivery(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "配送ID", required = true)
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestBody) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }
        
        String cancelReason = requestBody.containsKey("cancelReason") ? 
            requestBody.get("cancelReason").toString() : "运输员取消";
        
        OrderDelivery delivery = deliveryService.cancelDelivery(id, driver.getId(), cancelReason);
        return Result.success(delivery);
    }
    
    /**
     * 创建配送记录（订单服务调用，内部接口）
     */
    @Operation(summary = "创建配送记录", description = "订单服务调用，创建配送记录（内部接口）")
    @PostMapping("/create")
    public Result<OrderDelivery> createDelivery(
            @RequestParam("orderId") Long orderId,
            @RequestParam("deliveryAddress") String deliveryAddress,
            @RequestParam("receiverName") String receiverName,
            @RequestParam("receiverPhone") String receiverPhone) {
        OrderDelivery delivery = deliveryService.createDelivery(
            orderId, deliveryAddress, receiverName, receiverPhone);
        return Result.success(delivery);
    }

    /**
     * 司机主动接单附近路线段（智能调度模式）
     *
     * 司机从附近路线段大厅中选择一条路线段接单，
     * 系统直接创建配送记录并绑定路线，无需等待预创建的配送单。
     */
    @Operation(summary = "接单路线段（智能调度）",
               description = "司机从附近路线段列表选择一条路线直接接单")
    @PostMapping("/accept-segment")
    public Result<OrderDelivery> acceptSegment(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Map<String, Object> body) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }

        Long routeId      = body.get("routeId")     != null ? Long.parseLong(body.get("routeId").toString())     : null;
        Long vehicleId    = body.get("vehicleId")   != null ? Long.parseLong(body.get("vehicleId").toString())   : null;
        Long orderId      = body.get("orderId")     != null ? Long.parseLong(body.get("orderId").toString())     : null;
        Long batchId      = body.get("batchId")     != null ? Long.parseLong(body.get("batchId").toString())     : null;
        Long hubId        = body.get("hubId")       != null ? Long.parseLong(body.get("hubId").toString())       : null;
        Integer routeType = body.get("routeType")   != null ? Integer.parseInt(body.get("routeType").toString()) : 0;
        String startAddr  = body.get("startAddress") != null ? body.get("startAddress").toString() : "";
        String endAddr    = body.get("endAddress")   != null ? body.get("endAddress").toString()   : "";
        String recvName   = body.get("receiverName") != null ? body.get("receiverName").toString() : "";
        String recvPhone  = body.get("receiverPhone")!= null ? body.get("receiverPhone").toString(): "";

        if (routeId == null) {
            return Result.error("routeId 不能为空");
        }
        // 干线(routeType=1)和多停靠末端(routeType=2)的 orderId 允许为 null
        if (orderId == null && routeType != 1 && routeType != 2) {
            return Result.error("非干线/多停靠路线的 orderId 不能为空");
        }

        OrderDelivery delivery = deliveryService.acceptSegment(
            routeId, driver.getId(), vehicleId,
            orderId, startAddr, endAddr,
            recvName, recvPhone,
            routeType, batchId, hubId
        );

        // 多停靠末端路线（orderId=null，routeType=2）：接单后逐单通知 order-service → 派送中
        if (orderId == null && routeType != null && routeType == 2) {
            updateMultiStopOrdersStatus(routeId, 3, userIdHeader);
        }

        return Result.success(delivery);
    }

    /**
     * 干线司机确认到达 Hub 中转站（Hub-and-Spoke 专用）
     *
     * 触发流程：
     *   driver → logistics（MQ #13 HubArrivalMessage）
     *   logistics → 激活末端路线
     *   logistics → driver（MQ #14 LastMileActivateMessage × N条）
     *   driver → 末端配送单进入待接单大厅
     */
    @Operation(summary = "确认到达中转站（干线专用）",
               description = "干线司机到达 Hub 后调用，触发末端配送单创建流程")
    @PutMapping("/{id}/arrive-hub")
    public Result<OrderDelivery> arriveAtHub(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }
        OrderDelivery delivery = deliveryService.arriveAtHub(id, driver.getId());
        return Result.success(delivery);
    }

    /**
     * 末端司机逐站送达（多停靠路线专用）
     *
     * 请求体：{"orderId": X}
     * 流程：
     *   1. 标记 logistics_batch_item.item_status = 2
     *   2. 通知 order-service 该订单已完成
     *   3. 若该路线所有停靠点均已送达，自动将配送单状态更新为已送达(3)
     */
    @Operation(summary = "逐站送达（末端多停靠专用）",
               description = "司机到达某停靠点后调用，标记该订单已送达；全部完成后自动关闭配送单")
    @PutMapping("/{id}/complete-stop")
    public Result<String> completeStop(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) return Result.error("运输员信息不存在");

        OrderDelivery delivery = deliveryService.getDeliveryById(id);
        if (delivery == null) return Result.error("配送记录不存在");
        if (!driver.getId().equals(delivery.getDriverId()))
            throw new BusinessException(ResultCode.FORBIDDEN);

        Object orderIdObj = body.get("orderId");
        if (orderIdObj == null) return Result.error("orderId 不能为空");
        Long orderId = Long.parseLong(orderIdObj.toString());

        Long routeId = delivery.getRouteId();
        if (routeId == null) return Result.error("该配送单未关联路线，无法逐站操作");

        // 1. 标记 batch_item.item_status = 2，并判断是否全部完成
        Result<Map<String, Object>> stopRes = logisticsFeign.completeStop(routeId, orderId, userIdHeader);
        if (stopRes == null || stopRes.getData() == null) return Result.error("物流服务调用失败");
        boolean allDone = Boolean.TRUE.equals(stopRes.getData().get("allDone"));

        // 2. 通知 order-service 该订单进入待签收（状态=6），等顾客确认签收后转为已完成
        try {
            orderFeign.updateOrderStatus(orderId, Map.of("orderStatus", 6), userIdHeader, "admin");
        } catch (Exception e) {
            // 非致命错误，记录日志即可，不影响主流程
        }

        // 3. 若全部完成，自动关闭配送单
        if (allDone && delivery.getDeliveryStatus() != 3) {
            deliveryService.updateDeliveryStatus(id, driver.getId(), 3, null);
            return Result.success("本站已送达，全部停靠点完成，配送单已自动关闭");
        }
        return Result.success("本站已送达");
    }

    // ================================================================
    //  内部工具
    // ================================================================

    /**
     * 多停靠路线接单/开始运输时，逐单通知 order-service 更新订单状态
     * （用于 orderId=null 的多停靠末端配送单，无法在 Service 层单独更新）
     */
    private void updateMultiStopOrdersStatus(Long routeId, int orderStatus, String userIdHeader) {
        try {
            Result<List<Map<String, Object>>> stopsResult = logisticsFeign.getRouteStops(routeId, userIdHeader);
            if (stopsResult == null || stopsResult.getData() == null) return;
            for (Map<String, Object> stop : stopsResult.getData()) {
                Object stopOrderId = stop.get("orderId");
                if (stopOrderId == null) continue;
                Long oid = Long.parseLong(stopOrderId.toString());
                try {
                    orderFeign.updateOrderStatus(oid, Map.of("orderStatus", orderStatus), userIdHeader, "admin");
                } catch (Exception ignored) { /* 非致命，单独失败不影响整体 */ }
            }
        } catch (Exception e) {
            log.warn("[多停靠] 批量更新订单状态失败 routeId={}: {}", routeId, e.getMessage());
        }
    }
}

