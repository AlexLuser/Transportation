package com.fm.driver.controller;

import com.fm.common.dto.PageResult;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.driver.entity.Driver;
import com.fm.driver.entity.OrderDelivery;
import com.fm.driver.service.DeliveryService;
import com.fm.driver.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 配送订单管理Controller
 */
@Tag(name = "配送管理", description = "订单配送相关接口")
@RestController
@RequestMapping("/api/drivers/deliveries")
public class DeliveryController {
    
    @Autowired
    private DeliveryService deliveryService;
    
    @Autowired
    private DriverService driverService;
    
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
        
        OrderDelivery delivery = deliveryService.updateDeliveryStatus(id, driver.getId(), status, remark);
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
}

