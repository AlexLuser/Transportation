package com.fm.logistics.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.logistics.dto.LocationUpdateDTO;
import com.fm.logistics.entity.LogisticsTrack;
import com.fm.logistics.feign.DriverFeignClient;
import com.fm.logistics.service.LogisticsTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.List;

/**
 * 实时轨迹 Controller
 *
 * POST  /api/logistics/track/location          - 运输员上报当前 GPS 位置（高频调用）
 * GET   /api/logistics/track/{routeId}/latest  - 获取最新一条轨迹（买家看当前位置）
 * GET   /api/logistics/track/{routeId}/recent  - 获取最近 N 条轨迹（默认50条）
 * GET   /api/logistics/track/{routeId}/history - 获取全部轨迹历史（轨迹回放）
 */
@Tag(name = "实时轨迹", description = "运输员位置上报和轨迹查询接口")
@RestController
@RequestMapping("/api/logistics/track")
public class LogisticsTrackController {

    @Autowired
    private LogisticsTrackService trackService;

    // B6：注入 DriverFeignClient，用于将 userId 转换为真实 driverId
    @Autowired
    private DriverFeignClient driverFeignClient;

    /**
     * 运输员上报当前 GPS 位置（B6：userId → driverId 转换）
     * 建议客户端每 15~30 秒调用一次
     */
    @Operation(summary = "上报GPS位置", description = "运输员周期性上报位置，同步更新路线当前位置")
    @PostMapping("/location")
    public Result<LogisticsTrack> uploadLocation(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @RequestBody LocationUpdateDTO locationUpdate) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"driver".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅运输员可上报位置");
        }
        if (locationUpdate.getRouteId() == null
                || locationUpdate.getLatitude() == null
                || locationUpdate.getLongitude() == null) {
            return Result.error("路线ID、纬度、经度不能为空");
        }

        // B6：userId → driverId 转换（避免把 userId 直接当 driverId 写入轨迹表）
        Long userId = Long.parseLong(userIdHeader);
        Long driverId;
        try {
            Result<Map<String, Object>> driverResult = driverFeignClient.getDriverByUserId(userId);
            if (driverResult.getCode() != 200 || driverResult.getData() == null) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "运输员信息不存在，请先完善个人资料");
            }
            driverId = Long.valueOf(driverResult.getData().get("id").toString());
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "获取运输员信息失败：" + e.getMessage());
        }

        LogisticsTrack track = trackService.updateLocation(driverId, locationUpdate);
        return Result.success(track);
    }

    /**
     * 获取最新一条轨迹（运输员当前位置）
     */
    @Operation(summary = "获取当前位置", description = "获取运输员最新上报的位置（买家实时追踪）")
    @GetMapping("/{routeId}/latest")
    public Result<LogisticsTrack> getLatestTrack(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "路线ID") @PathVariable Long routeId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        LogisticsTrack track = trackService.getLatestTrack(routeId);
        if (track == null) {
            return Result.error("暂无位置信息，运输员尚未上报位置");
        }
        return Result.success(track);
    }

    /**
     * 获取最近 N 条轨迹（默认50条，最多200条）
     */
    @Operation(summary = "获取最近轨迹", description = "获取最近 N 条轨迹点（时间倒序），用于地图展示近期路径段")
    @GetMapping("/{routeId}/recent")
    public Result<List<LogisticsTrack>> getRecentTracks(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "路线ID") @PathVariable Long routeId,
            @Parameter(description = "条数，默认50，最多200") @RequestParam(defaultValue = "50") Integer limit) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(trackService.getLatestTracks(routeId, limit));
    }

    /**
     * 获取完整轨迹历史（时间正序，用于轨迹回放）
     */
    @Operation(summary = "获取完整轨迹历史", description = "按时间正序返回全部轨迹点，用于地图轨迹回放")
    @GetMapping("/{routeId}/history")
    public Result<List<LogisticsTrack>> getTrackHistory(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "路线ID") @PathVariable Long routeId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(trackService.getTrackByRouteId(routeId));
    }
}

