package com.fm.logistics.service.impl;

import com.fm.logistics.dto.HistoryContextDTO;
import com.fm.logistics.mapper.LogisticsRouteMapper;
import com.fm.logistics.mapper.LogisticsTrackMapper;
import com.fm.logistics.service.RouteHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RouteHistoryServiceImpl implements RouteHistoryService {

    private static final Logger log = LoggerFactory.getLogger(RouteHistoryServiceImpl.class);

    @Autowired
    private LogisticsTrackMapper trackMapper;

    @Autowired
    private LogisticsRouteMapper routeMapper;

    @Override
    public HistoryContextDTO buildContext(double startLat, double startLon,
                                          double endLat, double endLon) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        HistoryContextDTO ctx = new HistoryContextDTO();
        ctx.setCurrentHour(currentHour);
        ctx.setDayOfWeek(toChinese(now.getDayOfWeek()));
        ctx.setTimePeriod(detectTimePeriod(currentHour, now.getMinute()));

        // ── 1. 行驶速度统计（logistics_track，过去30天） ──────────────────
        List<Map<String, Object>> speedStats = querySpeed();
        double allDayAvg = computeListAvg(speedStats, "avgSpeed");
        double currentHourAvg = getHourValue(speedStats, currentHour, "avgSpeed");
        int sampleCount = getHourInt(speedStats, currentHour, "sampleCount");

        ctx.setAllDayAvgSpeedKmh(allDayAvg > 0 ? allDayAvg : 35.0);
        ctx.setCurrentHourAvgSpeedKmh(currentHourAvg > 0 ? currentHourAvg : ctx.getAllDayAvgSpeedKmh());
        ctx.setSpeedRatio(ctx.getAllDayAvgSpeedKmh() > 0
                ? ctx.getCurrentHourAvgSpeedKmh() / ctx.getAllDayAvgSpeedKmh() : 1.0);
        ctx.setSpeedSampleCount(sampleCount);

        // ── 2. 延误统计（logistics_route，过去30天） ─────────────────────
        List<Map<String, Object>> delayStats = queryDelay();
        ctx.setAllDayDelayMin(computeListAvg(delayStats, "avgDelayMin"));
        ctx.setCurrentHourDelayMin(getHourValue(delayStats, currentHour, "avgDelayMin"));

        // ── 3. 慢速热点（以起终点中心为圆心，约±0.1度≈10km 范围） ──────
        double midLat = (startLat + endLat) / 2;
        double midLon = (startLon + endLon) / 2;
        List<Map<String, Object>> rawZones = querySlowZones(midLat, midLon, 0.1);

        List<HistoryContextDTO.SlowZone> slowZones = new ArrayList<>();
        for (Map<String, Object> row : rawZones) {
            HistoryContextDTO.SlowZone zone = new HistoryContextDTO.SlowZone();
            zone.setLat(toDouble(row.get("latBucket")));
            zone.setLon(toDouble(row.get("lonBucket")));
            zone.setAvgSpeedKmh(toDouble(row.get("avgSpeed")));
            zone.setOccurrences(toInt(row.get("occurrences")));
            zone.setPeakHour(toInt(row.get("peakHour")));
            slowZones.add(zone);
        }
        ctx.setSlowZones(slowZones);

        // ── 4. 异常路线统计（起点附近约2km，近7天） ─────────────────────
        try {
            Map<String, Object> excStats = routeMapper.selectExceptionStats(startLat, startLon);
            if (excStats != null) {
                ctx.setExceptionCount(toInt(excStats.get("exceptionCount")));
                Object lastTime = excStats.get("lastExceptionTime");
                ctx.setLastExceptionDesc(lastTime != null ? lastTime.toString() : null);
            }
        } catch (Exception e) {
            log.warn("[RouteHistory] 异常路线统计查询失败: {}", e.getMessage());
            ctx.setExceptionCount(0);
        }

        log.info("[RouteHistory] 上下文构建完成: 当前时速={}km/h, 速度比={}, 慢速热点={}个, 异常路线={}次",
                String.format("%.1f", ctx.getCurrentHourAvgSpeedKmh()),
                String.format("%.2f", ctx.getSpeedRatio()),
                slowZones.size(), ctx.getExceptionCount());
        return ctx;
    }

    // ---- 查询封装（带异常保护，失败返回空列表） ----

    private List<Map<String, Object>> querySpeed() {
        try {
            return trackMapper.selectAvgSpeedByHour();
        } catch (Exception e) {
            log.warn("[RouteHistory] 速度统计查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> queryDelay() {
        try {
            return routeMapper.selectDelayStatsByHour();
        } catch (Exception e) {
            log.warn("[RouteHistory] 延误统计查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> querySlowZones(double midLat, double midLon, double range) {
        try {
            return trackMapper.selectSlowZones(
                    midLat - range, midLat + range,
                    midLon - range, midLon + range);
        } catch (Exception e) {
            log.warn("[RouteHistory] 慢速热点查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ---- 工具方法 ----

    private double computeListAvg(List<Map<String, Object>> stats, String valueKey) {
        if (stats.isEmpty()) return 0.0;
        return stats.stream()
                .mapToDouble(row -> toDouble(row.get(valueKey)))
                .filter(v -> v > 0)
                .average()
                .orElse(0.0);
    }

    private double getHourValue(List<Map<String, Object>> stats, int hour, String valueKey) {
        return stats.stream()
                .filter(row -> toInt(row.get("hourBucket")) == hour)
                .mapToDouble(row -> toDouble(row.get(valueKey)))
                .findFirst()
                .orElse(0.0);
    }

    private int getHourInt(List<Map<String, Object>> stats, int hour, String key) {
        return stats.stream()
                .filter(row -> toInt(row.get("hourBucket")) == hour)
                .mapToInt(row -> toInt(row.get(key)))
                .findFirst()
                .orElse(0);
    }

    private String toChinese(DayOfWeek dow) {
        String[] names = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return names[dow.getValue() - 1];
    }

    private String detectTimePeriod(int hour, int minute) {
        int total = hour * 60 + minute;
        if (total >= 420  && total < 570)  return "早高峰（07:00-09:30）";
        if (total >= 690  && total < 780)  return "午间高峰（11:30-13:00）";
        if (total >= 1020 && total < 1170) return "晚高峰（17:00-19:30）";
        return "平峰";
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        return ((Number) val).doubleValue();
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        return ((Number) val).intValue();
    }
}
