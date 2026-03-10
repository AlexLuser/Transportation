package com.fm.logistics.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.dto.ai.AIAnomalyCheckResponseDTO;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.service.LogisticsAIService;
import com.fm.logistics.service.LogisticsRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度管理 Controller（管理员/调度员使用）
 *
 * 接口设计：
 * GET  /api/logistics/dispatch/pending    - 获取所有待出发路线（调度大屏）
 * GET  /api/logistics/dispatch/active     - 获取所有运输中路线（实时监控）
 * GET  /api/logistics/dispatch/driver/{driverId} - 查看某运输员当前负责的路线
 * GET  /api/logistics/dispatch/anomaly    - 获取所有异常路线（告警大屏）
 * GET  /api/logistics/dispatch/anomaly/{routeId} - 检测指定路线异常
 */
@Tag(name = "调度管理", description = "运输调度大屏、实时监控和异常告警接口（管理员使用）")
@RestController
@RequestMapping("/api/logistics/dispatch")
public class LogisticsDispatchController {

    @Autowired
    private LogisticsRouteService routeService;

    @Autowired
    private LogisticsAIService aiService;

    /**
     * 获取所有待出发路线（等待派单）
     */
    @Operation(summary = "获取待出发路线列表", description = "调度大屏：显示所有已创建但尚未派单的路线")
    @GetMapping("/pending")
    public Result<List<LogisticsRoute>> getPendingRoutes(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问调度接口");
        }
        List<LogisticsRoute> routes = routeService.getPendingRoutes();
        return Result.success(routes);
    }

    /**
     * 获取所有运输中路线（实时监控大屏）
     */
    @Operation(summary = "获取运输中路线列表", description = "实时监控大屏：显示所有正在运输中的路线及其当前位置")
    @GetMapping("/active")
    public Result<List<LogisticsRoute>> getActiveRoutes(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问调度接口");
        }
        // 查询所有运输中（status=1）的路线
        List<LogisticsRoute> allRoutes = routeService.list();
        List<LogisticsRoute> activeRoutes = allRoutes.stream()
                .filter(r -> Integer.valueOf(1).equals(r.getRouteStatus()))
                .toList();
        return Result.success(activeRoutes);
    }

    /**
     * 查看某运输员当前负责的路线
     */
    @Operation(summary = "查看运输员路线", description = "查看指定运输员当前承接的所有路线")
    @GetMapping("/driver/{driverId}")
    public Result<List<LogisticsRoute>> getRoutesByDriver(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "运输员ID") @PathVariable Long driverId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问调度接口");
        }
        List<LogisticsRoute> routes = routeService.getRoutesByDriverId(driverId);
        return Result.success(routes);
    }

    /**
     * 获取所有异常路线（告警大屏）
     * 对每条运输中路线执行异常检测，返回有异常的路线列表
     */
    @Operation(summary = "获取异常路线列表", description = "告警大屏：对所有运输中路线批量执行异常检测")
    @GetMapping("/anomaly")
    public Result<List<AIAnomalyCheckResponseDTO>> getAnomalyRoutes(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问调度接口");
        }
        // 查询所有运输中路线
        List<LogisticsRoute> allRoutes = routeService.list();
        List<AIAnomalyCheckResponseDTO> anomalyResults = new ArrayList<>();
        for (LogisticsRoute route : allRoutes) {
            if (Integer.valueOf(1).equals(route.getRouteStatus())) {
                AIAnomalyCheckResponseDTO result = aiService.checkAnomaly(route.getId());
                if (Boolean.TRUE.equals(result.getHasAnomaly())) {
                    anomalyResults.add(result);
                }
            }
        }
        return Result.success(anomalyResults);
    }

    /**
     * 检测指定路线的异常
     */
    @Operation(summary = "检测路线异常", description = "对指定路线执行异常检测（超时、信号丢失、路线偏离等）")
    @GetMapping("/anomaly/{routeId}")
    public Result<AIAnomalyCheckResponseDTO> checkRouteAnomaly(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "路线ID") @PathVariable Long routeId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        AIAnomalyCheckResponseDTO result = aiService.checkAnomaly(routeId);
        return Result.success(result);
    }
}

