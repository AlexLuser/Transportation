package com.fm.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.logistics.config.LLMProperties;
import com.fm.logistics.dto.LocationUpdateDTO;
import com.fm.logistics.dto.llm.HistoricalStats;
import com.fm.logistics.dto.llm.LLMEtaResult;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.entity.LogisticsTrack;
import com.fm.logistics.mapper.LogisticsRouteMapper;
import com.fm.logistics.mapper.LogisticsTrackMapper;
import com.fm.logistics.service.HistoricalAnalysisService;
import com.fm.logistics.service.LLMService;
import com.fm.logistics.service.LogisticsTrackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class LogisticsTrackServiceImpl implements LogisticsTrackService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsTrackServiceImpl.class);

    @Autowired
    private LogisticsTrackMapper trackMapper;

    @Autowired
    private LogisticsRouteMapper routeMapper;

    @Autowired
    private LLMService llmService;

    @Autowired
    private HistoricalAnalysisService historicalAnalysisService;

    @Autowired
    private LLMProperties llmProps;

    @Override
    @Transactional
    public LogisticsTrack uploadLocation(Long driverId, LocationUpdateDTO dto) {
        // 验证路线是否存在
        LogisticsRoute route = routeMapper.selectById(dto.getRouteId());
        if (route == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "物流路线不存在");
        }
        // 注意：不在此处做 driverId 相等性校验。
        // 原因：logistics_route.driver_id 存的是 driver_info.id（司机档案主键），
        //       而 JWT userId 是 user_info.id（登录账号主键），两者不是同一字段，
        //       相等性检查交由 Gateway 的 roleCode=driver 校验已足够。
        if (route.getRouteStatus() == 2) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "该路线已送达，无需上报位置");
        }
        if (route.getRouteStatus() == null || route.getRouteStatus() == 3) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "路线状态异常，无法上报位置");
        }

        // 保存轨迹点
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
        track.setTrackTime(dto.getTrackTimeMs() != null ?
                new Date(dto.getTrackTimeMs()) : new Date());
        trackMapper.insert(track);

        // 同步更新路线的当前位置
        route.setCurrentLat(dto.getLatitude());
        route.setCurrentLng(dto.getLongitude());
        route.setCurrentAddress(dto.getAddress());
        route.setLastTrackTime(track.getTrackTime());
        // 如果路线还是待出发且运输员开始上报位置，自动变更为运输中
        if (route.getRouteStatus() == 0) {
            route.setRouteStatus(1);
        }
        routeMapper.updateById(route);

        // 每 N 次 GPS 上报触发一次 LLM 动态 ETA 重算
        // 使用轨迹总数取模，避免高频调用（默认每10次更新一次）
        long trackCount = trackMapper.countByRouteId(dto.getRouteId());
        int interval = llmProps.getEtaUpdateInterval() > 0 ? llmProps.getEtaUpdateInterval() : 10;
        if (trackCount % interval == 0) {
            log.debug("[LLM] 路线 {} 第 {} 次上报，触发 ETA 重算", route.getRouteNo(), trackCount);
            triggerLLMEtaUpdate(route, dto.getLatitude(), dto.getLongitude(),
                    dto.getSpeed() != null ? dto.getSpeed() : 0.0);
        }

        return track;
    }

    /**
     * 异步触发 LLM 动态 ETA 重算
     *
     * 步骤：
     *   1. 计算已配送时长（分钟）
     *   2. 用 Haversine 估算剩余距离
     *   3. 调用 HistoricalAnalysisService 获取历史统计（含进度诊断）
     *   4. 调用 LLMService.predictETA()（含 Prompt 构建）
     *   5. 将新 ETA 和延误风险写回 logistics_route
     */
    @Async
    public void triggerLLMEtaUpdate(LogisticsRoute route,
                                     Double currentLat,
                                     Double currentLng,
                                     double currentSpeedKmh) {
        try {
            // 1. 计算已用时（分钟）
            Date startTime = route.getCreateTime();
            int elapsedMinutes = startTime != null
                    ? (int) ((System.currentTimeMillis() - startTime.getTime()) / 60_000)
                    : 0;

            // 2. 估算剩余距离（当前位置到终点的 Haversine 直线距离，单位 km）
            double remainingDistKm = haversineKm(
                    currentLat, currentLng,
                    route.getEndLat(), route.getEndLng());

            // 3. 进度比例
            double totalDistKm = haversineKm(
                    route.getStartLat(), route.getStartLng(),
                    route.getEndLat(), route.getEndLng());
            double progressRatio = totalDistKm > 0
                    ? Math.min(1.0, (totalDistKm - remainingDistKm) / totalDistKm)
                    : 0;

            // 4. 获取历史统计（含进度诊断文字）
            int departHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            HistoricalStats historical = historicalAnalysisService.analyzeByDestination(
                    route.getEndAddress(), departHour);

            // 5. 调用 LLM 预测
            LLMEtaResult etaResult = llmService.predictETA(
                    route, currentLat, currentLng,
                    currentSpeedKmh, elapsedMinutes, remainingDistKm, historical);

            // 6. 写回路线（不触发其他业务逻辑，只更新 ETA 相关字段）
            LogisticsRoute update = new LogisticsRoute();
            update.setId(route.getId());
            update.setEstimatedArrivalTime(etaResult.getPredictedArrivalTime());

            // 将延误风险追加写入 ai_analysis（JSON 合并简化处理）
            String etaAnalysis = String.format(
                    "{\"etaRemainingMin\":%d,\"delayRisk\":%d,\"delayReasons\":%s,\"isFallback\":%b}",
                    etaResult.getRemainingMinutes(),
                    etaResult.getDelayRisk(),
                    listToJson(etaResult.getDelayReasons()),
                    etaResult.isFallback());
            update.setAiAnalysis(etaAnalysis);
            routeMapper.updateById(update);

            log.info("[LLM] 路线 {} ETA 更新：剩余{}分钟，延误风险={}，isFallback={}",
                    route.getRouteNo(), etaResult.getRemainingMinutes(),
                    etaResult.getDelayRisk(), etaResult.isFallback());

        } catch (Exception e) {
            log.warn("[LLM] 路线 {} ETA 更新失败: {}", route.getRouteNo(), e.getMessage());
        }
    }

    // ---------- 工具方法 ----------

    /** Haversine 直线距离（km），用于剩余距离估算 */
    private double haversineKm(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return 0;
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1))
                 * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String listToJson(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public List<LogisticsTrack> getTracksByRouteId(Long routeId) {
        LambdaQueryWrapper<LogisticsTrack> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsTrack::getRouteId, routeId)
                .orderByAsc(LogisticsTrack::getTrackTime);
        return trackMapper.selectList(wrapper);
    }

    @Override
    public List<LogisticsTrack> getLatestTracks(Long routeId, Integer limit) {
        int safeLimit = (limit == null || limit <= 0 || limit > 200) ? 50 : limit;
        return trackMapper.selectLatestTracks(routeId, safeLimit);
    }

    @Override
    public LogisticsTrack getLatestTrack(Long routeId) {
        List<LogisticsTrack> tracks = trackMapper.selectLatestTracks(routeId, 1);
        return tracks.isEmpty() ? null : tracks.get(0);
    }
}

