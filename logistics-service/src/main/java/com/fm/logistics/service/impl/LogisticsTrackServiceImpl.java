package com.fm.logistics.service.impl;

import com.fm.logistics.dto.LocationUpdateDTO;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.entity.LogisticsTrack;
import com.fm.logistics.mapper.LogisticsRouteMapper;
import com.fm.logistics.mapper.LogisticsTrackMapper;
import com.fm.logistics.service.LogisticsTrackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class LogisticsTrackServiceImpl implements LogisticsTrackService {

    @Autowired
    private LogisticsTrackMapper logisticsTrackMapper;

    @Autowired
    private LogisticsRouteMapper logisticsRouteMapper;  // 用于同步更新路线当前位置

    @Override
    @Transactional
    public LogisticsTrack updateLocation(Long driverId, LocationUpdateDTO dto) {
        // 1. 构建轨迹记录（所有字段）
        LogisticsTrack track = new LogisticsTrack();
        track.setRouteId(dto.getRouteId());
        track.setDriverId(driverId);
        track.setLatitude(dto.getLatitude());
        track.setLongitude(dto.getLongitude());
        track.setAltitude(dto.getAltitude());
        track.setSpeed(dto.getSpeed());
        track.setHeading(dto.getHeading());
        track.setAccuracy(dto.getAccuracy());
        track.setAddress(dto.getAddress());
        // trackTime 由服务端统一赋值，不信任客户端时间
        track.setTrackTime(new Date());

        // 2. 写入轨迹表
        logisticsTrackMapper.insert(track);

        // 3. 同步更新路线的当前位置（让买家能看到最新位置）
        LogisticsRoute routeUpdate = new LogisticsRoute();
        routeUpdate.setId(dto.getRouteId());
        routeUpdate.setCurrentLatitude(dto.getLatitude());
        routeUpdate.setCurrentLongitude(dto.getLongitude());
        routeUpdate.setCurrentAddress(dto.getAddress());
        routeUpdate.setLastTrackTime(track.getTrackTime());
        logisticsRouteMapper.updateById(routeUpdate);

        return track;
    }

    @Override
    public LogisticsTrack getLatestTrack(Long routeId) {
        return logisticsTrackMapper.selectLatestOne(routeId);
    }

    @Override
    public List<LogisticsTrack> getLatestTracks(Long routeId, Integer limit) {
        int safeLimit = Math.min(limit, 200);  // 最多返回200条，防止请求过大
        return logisticsTrackMapper.selectLatestTracks(routeId, safeLimit);
    }

    @Override
    public List<LogisticsTrack> getTrackByRouteId(Long routeId) {
        return logisticsTrackMapper.selectAllByRouteId(routeId);
    }
}
