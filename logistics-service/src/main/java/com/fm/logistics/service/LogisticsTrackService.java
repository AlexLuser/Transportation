package com.fm.logistics.service;

import com.fm.logistics.dto.LocationUpdateDTO;
import com.fm.logistics.entity.LogisticsTrack;

import java.util.List;

/**
 * 物流轨迹服务接口
 */
public interface LogisticsTrackService {

    /**
     * 运输员上报位置（保存轨迹点，并更新路线当前位置）
     *
     * @param driverId      运输员ID（从 JWT Header 中获取）
     * @param locationUpdate 位置信息
     * @return 保存的轨迹点
     */
    LogisticsTrack uploadLocation(Long driverId, LocationUpdateDTO locationUpdate);

    /**
     * 获取路线的完整轨迹列表（按时间正序）
     */
    List<LogisticsTrack> getTracksByRouteId(Long routeId);

    /**
     * 获取路线最新的 N 条轨迹（用于实时展示）
     */
    List<LogisticsTrack> getLatestTracks(Long routeId, Integer limit);

    /**
     * 获取路线最新一条轨迹（当前位置）
     */
    LogisticsTrack getLatestTrack(Long routeId);
}

