package com.fm.logistics.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.logistics.dto.CreateRouteRequestDTO;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.service.LogisticsRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 物流路线管理 Controller
 *
 * 接口设计：
 * POST   /api/logistics/routes                     - 创建路线（order-service 内部调用）
 * GET    /api/logistics/routes/order/{orderId}      - 买家/商户：按订单查路线详情
 * GET    /api/logistics/routes/{routeId}            - 按路线ID查详情
 * GET    /api/logistics/routes/no/{routeNo}         - 按路线编号查详情（对外展示的编号）
 * PUT    /api/logistics/routes/{routeId}/bind       - 绑定运输员（接单时调用）
 * PUT    /api/logistics/routes/{routeId}/status     - 更新路线状态
 * PUT    /api/logistics/routes/{routeId}/plan-route - 更新计划路线（GeoJSON）
 */
@Tag(name = "物流路线管理", description = "物流路线创建、查询及状态管理接口")
@RestController
@RequestMapping("/api/logistics/routes")
public class LogisticsRouteController {

    @Autowired
    private LogisticsRouteService routeService;

    /**
     * 创建物流路线（order-service 发货时调用，内部接口）
     */
    @Operation(summary = "创建物流路线", description = "订单发货时由 order-service 调用，自动创建物流路线")
    @PostMapping
    public Result<LogisticsRoute> createRoute(@RequestBody CreateRouteRequestDTO request) {
        if (request.getOrderId() == null || request.getWarehouseId() == null
                || !StringUtils.hasText(request.getStartAddress())
                || !StringUtils.hasText(request.getEndAddress())) {
            return Result.error("订单ID、仓库ID、发货地址和收货地址不能为空");
        }
        LogisticsRoute route = routeService.createRoute(request);
        return Result.success(route);
    }

    /**
     * 按订单ID查询物流路线详情（买家、商户、运输员均可查询）
     */
    @Operation(summary = "按订单ID查询物流路线", description = "买家/商户/运输员查看订单的物流轨迹和当前位置")
    @GetMapping("/order/{orderId}")
    public Result<RouteDetailDTO> getRouteByOrderId(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "订单ID") @PathVariable Long orderId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        RouteDetailDTO detail = routeService.getRouteDetailByOrderId(orderId);
        if (detail == null) {
            return Result.error("该订单的物流路线尚未创建");
        }
        return Result.success(detail);
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
        if (detail == null) {
            return Result.error("物流路线不存在");
        }
        return Result.success(detail);
    }

    /**
     * 按路线编号查询详情（对外公示的编号，如物流单号）
     */
    @Operation(summary = "按路线编号查询详情", description = "输入物流单号（如 LR202403070001）查询物流进度")
    @GetMapping("/no/{routeNo}")
    public Result<RouteDetailDTO> getRouteByNo(
            @Parameter(description = "物流路线编号") @PathVariable String routeNo) {
        RouteDetailDTO detail = routeService.getRouteDetailByRouteNo(routeNo);
        if (detail == null) {
            return Result.error("物流路线不存在");
        }
        return Result.success(detail);
    }

    /**
     * 绑定运输员（driver-service 接单时调用）
     * 请求体：{"driverId": 1, "deliveryId": 1}
     */
    @Operation(summary = "绑定运输员", description = "运输员接单时，将运输员信息绑定到物流路线")
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
     * 更新路线状态（管理员/系统调用）
     * 请求体：{"routeStatus": 2}
     */
    @Operation(summary = "更新路线状态", description = "0=待出发, 1=运输中, 2=已送达, 3=异常")
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
        Integer routeStatus = body.get("routeStatus");
        if (routeStatus == null) {
            return Result.error("路线状态不能为空");
        }
        LogisticsRoute route = routeService.updateRouteStatus(routeId, routeStatus);
        return Result.success(route);
    }

    /**
     * 更新计划路线（GeoJSON 格式）
     * 请求体：{"plannedRoute": "{...GeoJSON...}"}
     */
    @Operation(summary = "更新计划路线", description = "设置或更新路线的 GeoJSON 计划路线（可由 AI 接口生成后调用此接口保存）")
    @PutMapping("/{routeId}/plan-route")
    public Result<LogisticsRoute> updatePlannedRoute(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "路线ID") @PathVariable Long routeId,
            @RequestBody Map<String, String> body) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可更新计划路线");
        }
        String plannedRoute = body.get("plannedRoute");
        if (!StringUtils.hasText(plannedRoute)) {
            return Result.error("计划路线不能为空");
        }
        LogisticsRoute route = routeService.updatePlannedRoute(routeId, plannedRoute);
        return Result.success(route);
    }
}

