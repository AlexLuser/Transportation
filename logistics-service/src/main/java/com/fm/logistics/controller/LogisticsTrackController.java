package com.fm.logistics.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.logistics.dto.LocationUpdateDTO;
import com.fm.logistics.entity.LogisticsTrack;
import com.fm.logistics.service.LogisticsTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实时轨迹 Controller
 *
 * 接口设计：
 * POST  /api/logistics/track/location         - 运输员上报当前 GPS 位置（高频调用）
 * GET   /api/logistics/track/{routeId}/history - 获取完整轨迹历史（用于轨迹回放）
 * GET   /api/logistics/track/{routeId}/latest  - 获取最新轨迹点（买家实时查看位置）
 * GET   /api/logistics/track/{routeId}/recent  - 获取最近 N 条轨迹（默认50条）
 */
@Tag(name = "实时轨迹追踪", description = "运输员位置上报和实时轨迹查询接口")
@RestController
@RequestMapping("/api/logistics/track")
public class LogisticsTrackController {

    @Autowired
    private LogisticsTrackService trackService;

    /**
     * 运输员上报当前 GPS 位置
     * 建议客户端每 15~30 秒调用一次
     */
    @Operation(summary = "上报位置", description = "运输员 App 周期性上报 GPS 位置，更新实时轨迹")
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
            return Result.error("路线ID、纬度和经度不能为空");
        }
        Long driverId = Long.parseLong(userIdHeader);
        LogisticsTrack track = trackService.uploadLocation(driverId, locationUpdate);
        return Result.success(track);
    }

    /**
     * 获取完整轨迹历史（时间正序，用于轨迹回放）
     */
    @Operation(summary = "获取完整轨迹历史", description = "按时间正序返回全部轨迹点，可用于地图轨迹回放")
    @GetMapping("/{routeId}/history")
    public Result<List<LogisticsTrack>> getTrackHistory(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "路线ID") @PathVariable Long routeId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        List<LogisticsTrack> tracks = trackService.getTracksByRouteId(routeId);
        return Result.success(tracks);
    }

    /**
     * 获取最新一条轨迹点（当前位置）
     * 买家查看运输员当前在哪
     */
    @Operation(summary = "获取当前位置", description = "获取运输员最新上报的位置（买家实时追踪用）")
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
     * 获取最近 N 条轨迹（时间倒序，默认50条）
     */
    @Operation(summary = "获取最近轨迹", description = "获取最近 N 条轨迹点（时间倒序），用于实时展示近期轨迹段")
    @GetMapping("/{routeId}/recent")
    public Result<List<LogisticsTrack>> getRecentTracks(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "路线ID") @PathVariable Long routeId,
            @Parameter(description = "条数限制，默认50，最大200")
            @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        List<LogisticsTrack> tracks = trackService.getLatestTracks(routeId, limit);
        return Result.success(tracks);
    }
}

