package com.fm.logistics.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.logistics.dto.CreateRouteRequestDTO;
import com.fm.logistics.dto.CreateRouteResponseDTO;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.service.LogisticsRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 物流路线管理 Controller
 *
 * POST   /api/logistics/routes                      - 创建路线（order-service 内部调用）
 * GET    /api/logistics/routes/order/{orderId}       - 按订单ID查路线详情
 * GET    /api/logistics/routes/{routeId}             - 按路线ID查详情
 * GET    /api/logistics/routes/no/{routeNo}          - 按路线编号查详情（物流单号）
 * PUT    /api/logistics/routes/{routeId}/bind        - 绑定运输员（接单时调用）
 * PUT    /api/logistics/routes/{routeId}/status      - 更新路线状态
 */
@Tag(name = "物流路线管理", description = "路线创建、查询及状态管理接口")
@RestController
@RequestMapping("/api/logistics/routes")
public class LogisticsRouteController {

    @Autowired
    private LogisticsRouteService routeService;

    /**
     * 创建物流路线（order-service 发货时调用）
     */
    @Operation(summary = "创建物流路线", description = "订单发货时由 order-service 调用，自动创建物流路线")
    @PostMapping
    public Result<CreateRouteResponseDTO> createRoute(@RequestBody CreateRouteRequestDTO request) {
        if (request.getOrderId() == null || request.getWarehouseId() == null
                || !StringUtils.hasText(request.getStartAddress())
                || !StringUtils.hasText(request.getEndAddress())) {
            return Result.error("订单ID、仓库ID、出发地址和收货地址不能为空");
        }
        CreateRouteResponseDTO body = routeService.createRoute(request);
        return Result.success(body);
    }

    /**
     * 按订单ID查询路线详情（买家查物流进度）
     */
    @Operation(summary = "按订单ID查询路线详情")
    @GetMapping("/order/{orderId}")
    public Result<RouteDetailDTO> getRouteByOrderId(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "订单ID") @PathVariable Long orderId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        RouteDetailDTO detail = routeService.getRouteDetailByOrderId(orderId);
        return Result.success(detail);
    }

    /**
     * 查询订单全程物流追踪（所有路线段，按时间升序）
     * 供顾客、商家、管理员的订单详情页展示完整物流流程。
     */
    @Operation(summary = "查询订单全程物流追踪")
    @GetMapping("/order/{orderId}/journey")
    public Result<List<RouteDetailDTO>> getOrderJourney(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "订单ID") @PathVariable Long orderId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(routeService.getOrderJourney(orderId));
    }

    /**
     * 按路线ID查询详情
     */
    @Operation(summary = "按路线ID查询详情")
    @GetMapping("/{routeId}")
    public Result<RouteDetailDTO> getRouteById(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "路线ID") @PathVariable Long routeId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        RouteDetailDTO detail = routeService.getRouteDetailById(routeId);
        return Result.success(detail);
    }

    /**
     * 按路线编号查询详情（输入物流单号，如 LR202603110001）
     */
    @Operation(summary = "按物流单号查询详情", description = "输入物流单号（如 LR202603110001）查询物流进度，无需登录")
    @GetMapping("/no/{routeNo}")
    public Result<RouteDetailDTO> getRouteByNo(
            @Parameter(description = "物流路线编号") @PathVariable String routeNo) {
        RouteDetailDTO detail = routeService.getRouteDetailByRouteNo(routeNo);
        return Result.success(detail);
    }

    /**
     * 绑定运输员（driver-service 接单时调用）
     * 请求体：{"driverId": 1, "deliveryId": 1}
     */
    @Operation(summary = "绑定运输员", description = "运输员接单时将自身信息绑定到路线，路线状态变为运输中")
    @PutMapping("/{routeId}/bind")
    public Result<LogisticsRoute> bindDriver(
            @Parameter(description = "路线ID") @PathVariable Long routeId,
            @RequestBody Map<String, Long> body) {
        Long driverId = body.get("driverId");
        Long deliveryId = body.get("deliveryId");
        if (driverId == null) {
            return Result.error("运输员ID不能为空");
        }
        LogisticsRoute route = routeService.bindDriver(routeId, driverId, deliveryId);
        return Result.success(route);
    }

    /**
     * 更新路线状态（管理员 / 运输员）
     * 请求体：{"status": 2}
     */
    @Operation(summary = "更新路线状态", description = "0=待出发，1=运输中，2=已送达，3=异常")
    @PutMapping("/{routeId}/status")
    public Result<LogisticsRoute> updateStatus(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "路线ID") @PathVariable Long routeId,
            @RequestBody Map<String, Integer> body) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode) && !"driver".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权更新路线状态");
        }
        Integer status = body.get("status");
        if (status == null) {
            return Result.error("状态值不能为空");
        }
        LogisticsRoute route = routeService.updateRouteStatus(routeId, status);
        return Result.success(route);
    }

    /**
     * 获取所有待出发路线（管理员调度大屏）
     */
    @Operation(summary = "获取待出发路线列表", description = "调度大屏：显示所有等待派单的路线")
    @GetMapping("/pending")
    public Result<List<LogisticsRoute>> getPendingRoutes(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问");
        }
        return Result.success(routeService.getPendingRoutes());
    }

    /**
     * 查看运输员负责的路线列表
     */
    @Operation(summary = "查看运输员路线列表", description = "运输员查看自己负责的全部路线")
    @GetMapping("/driver/{driverId}")
    public Result<List<LogisticsRoute>> getRoutesByDriver(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "运输员ID") @PathVariable Long driverId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(routeService.getRoutesByDriverId(driverId));
    }

    /**
     * 查询批次下所有路线段（干线+末端，Hub-and-Spoke用）
     */
    @Operation(summary = "查询批次路线段列表", description = "返回批次下干线路线(segment_type=1)和所有末端路线(segment_type=2)")
    @GetMapping("/batch/{batchId}")
    public Result<List<LogisticsRoute>> getRoutesByBatch(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @PathVariable Long batchId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(routeService.getRoutesByBatchId(batchId));
    }
}

