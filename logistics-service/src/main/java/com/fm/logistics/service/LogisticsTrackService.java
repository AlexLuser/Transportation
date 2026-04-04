package com.fm.logistics.service;

import com.fm.logistics.dto.LocationUpdateDTO;
import com.fm.logistics.entity.LogisticsTrack;

import java.util.List;

public interface LogisticsTrackService {
    /* 更新位置 */
    LogisticsTrack updateLocation(Long driverId, LocationUpdateDTO locationUpdateDTO);

    /* 获取轨迹 */
    LogisticsTrack getLatestTrack(Long routeId);

    /* 获取轨迹历史 */
    List<LogisticsTrack> getLatestTracks(Long routeId, Integer limit);

    /* 获取轨迹全部 */
    List<LogisticsTrack> getTrackByRouteId(Long routeId);
}
