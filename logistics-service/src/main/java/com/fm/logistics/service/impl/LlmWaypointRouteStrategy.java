package com.fm.logistics.service.impl;

import com.fm.logistics.client.DeepSeekClient;
import com.fm.logistics.dto.GeoJsonLineString;
import com.fm.logistics.dto.HistoryContextDTO;
import com.fm.logistics.dto.LlmDecisionResult;
import com.fm.logistics.dto.RouteResultDTO;
import com.fm.logistics.service.RouteHistoryService;
import com.fm.logistics.service.RouteStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 方案B：LLM 决策战略路点 + A* 分段执行策略
 *
 * 执行流程：
 *   1. 从数据库读取历史速度、慢速热点、异常记录等统计数据
 *   2. 将起终点 + 历史数据发给 DeepSeek，让 LLM 决定是否需要绕路路点（0-2个）
 *   3. 按 [起点 → 路点1 → 路点2 → 终点] 顺序分段调用 A*
 *   4. 拼接各段坐标，汇总总距离和总时间
 *   5. LLM 调用失败或路点无效时，自动降级为直接 A*
 *
 * LLM 在此策略中的参与深度：
 *   - 决定"走哪个方向/区域"（战略层），而非"走哪条路"（战术层）
 *   - A* 保证每段路线都在真实路网上，消除 LLM 幻觉路点的影响
 */
@Component
public class LlmWaypointRouteStrategy implements RouteStrategy {

    private static final Logger log = LoggerFactory.getLogger(LlmWaypointRouteStrategy.class);

    @Autowired
    private AStarRouteStrategy astarStrategy;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private RouteHistoryService historyService;

    @Override
    public String strategyName() {
        return "LLM_WAYPOINT";
    }

    @Override
    public RouteResultDTO plan(double startLat, double startLon,
                               double endLat, double endLon,
                               LocalDateTime plannedTime) {
        log.info("[{}] 开始规划: ({},{}) -> ({},{}), plannedTime={}", strategyName(), startLat, startLon, endLat, endLon, plannedTime);

        // ── 第一步：查询历史数据 ─────────────────────────────────────────
        HistoryContextDTO history = historyService.buildContext(startLat, startLon, endLat, endLon, plannedTime);

        // ── 第二步：调用 LLM 决策战略路点 ───────────────────────────────
        LlmDecisionResult decision;
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt   = buildUserPrompt(startLat, startLon, endLat, endLon, history);
            String llmResponse  = deepSeekClient.callApi(systemPrompt, userPrompt);
            decision = deepSeekClient.parseDecisionResult(llmResponse);

            int waypointCount = (decision.getWaypoints() != null) ? decision.getWaypoints().size() : 0;
            log.info("[{}] LLM策略={}，有效路点数={}，置信度={}",
                    strategyName(), decision.getStrategy(), waypointCount, decision.getConfidenceLevel());

        } catch (Exception e) {
            log.warn("[{}] LLM调用失败，降级为直接A*: {}", strategyName(), e.getMessage());
            return astarStrategy.plan(startLat, startLon, endLat, endLon, plannedTime);
        }

        // ── 第三步：构建分段路点列表 ──────────────────────────────────────
        List<double[]> segmentPoints = buildSegmentPoints(startLat, startLon, endLat, endLon, decision);

        // LLM 输出空路点时，直接 A* 即可（但保留 LLM 的 decision 信息）
        if (segmentPoints.size() == 2) {
            log.info("[{}] LLM建议直达策略，调用单段A*", strategyName());
            RouteResultDTO direct = astarStrategy.plan(startLat, startLon, endLat, endLon, plannedTime);
            if (direct.isSuccess()) {
                direct.setLlmDecision(decision);
                direct.setLlmEnhanced(true);
            }
            return direct;
        }

        // ── 第四步：分段 A* 规划并拼接结果 ──────────────────────────────
        RouteResultDTO merged = runSegmentedPlanning(segmentPoints);

        if (!merged.isSuccess()) {
            log.warn("[{}] 分段规划失败（{}），降级为直接A*", strategyName(), merged.getErrorMsg());
            return astarStrategy.plan(startLat, startLon, endLat, endLon, plannedTime);
        }

        // 若 LLM 提供了预期速度，用它修正总时间
        if (decision.getExpectedSpeedKmh() != null && decision.getExpectedSpeedKmh() > 0) {
            long adjustedMs = (long) (merged.getDistanceMeters() / (decision.getExpectedSpeedKmh() / 3.6) * 1000);
            merged.setDurationMs(adjustedMs);
        }

        merged.setLlmDecision(decision);
        merged.setLlmEnhanced(true);

        log.info("[{}] 分段规划完成: 段数={}，总距离={}m，总时间={}ms",
                strategyName(), segmentPoints.size() - 1,
                String.format("%.0f", merged.getDistanceMeters()), merged.getDurationMs());
        return merged;
    }

    // ---- 路段构建 ----

    /**
     * 构建有序路段端点列表：[起点, 路点1, 路点2, 终点]
     * 过滤掉不在上海范围内的路点（防止 LLM 幻觉坐标）
     */
    private List<double[]> buildSegmentPoints(double startLat, double startLon,
                                               double endLat, double endLon,
                                               LlmDecisionResult decision) {
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{startLat, startLon});

        if (decision.getWaypoints() != null) {
            for (LlmDecisionResult.WaypointPoint wp : decision.getWaypoints()) {
                if (wp.getLat() == null || wp.getLon() == null) continue;
                if (!isWithinShanghaiRegion(wp.getLat(), wp.getLon())) {
                    log.warn("[{}] 路点({},{})超出上海范围，已跳过", strategyName(), wp.getLat(), wp.getLon());
                    continue;
                }
                points.add(new double[]{wp.getLat(), wp.getLon()});
                log.info("[{}] 采纳LLM路点: ({},{}) - {}", strategyName(), wp.getLat(), wp.getLon(), wp.getLabel());
            }
        }

        points.add(new double[]{endLat, endLon});
        return points;
    }

    /**
     * 对每段分别执行 A*，拼接路线坐标、累加距离和时间
     * 相邻段的衔接点只保留一次（避免重复坐标）
     */
    private RouteResultDTO runSegmentedPlanning(List<double[]> points) {
        GeoJsonLineString mergedRoute = new GeoJsonLineString();
        double totalDistance = 0;
        long totalDuration = 0;

        for (int i = 0; i < points.size() - 1; i++) {
            double[] from = points.get(i);
            double[] to   = points.get(i + 1);

            RouteResultDTO seg = astarStrategy.plan(from[0], from[1], to[0], to[1], null);
            if (!seg.isSuccess()) {
                return RouteResultDTO.error("第" + (i + 1) + "段规划失败: " + seg.getErrorMsg());
            }

            totalDistance += seg.getDistanceMeters();
            totalDuration += seg.getDurationMs();

            // 合并坐标：第一段从头开始，后续段跳过第一个点（与上段末尾重复）
            List<double[]> coords = seg.getRoutePoints().getCoordinates();
            int startIdx = (i == 0) ? 0 : 1;
            for (int j = startIdx; j < coords.size(); j++) {
                // GeoJsonLineString 内部存储为 [lng, lat]，addPoint 参数为 (lat, lon)
                mergedRoute.addPoint(coords.get(j)[1], coords.get(j)[0]);
            }
        }

        return RouteResultDTO.success(totalDistance, totalDuration, mergedRoute);
    }

    /** 上海大致地理范围校验（经度120.8-122.2，纬度30.6-31.9） */
    private boolean isWithinShanghaiRegion(double lat, double lon) {
        return lat >= 30.6 && lat <= 31.9 && lon >= 120.8 && lon <= 122.2;
    }

    // ---- Prompt 构建 ----

    private String buildSystemPrompt() {
        return "你是一个物流路线策略专家。根据历史交通数据，决定是否需要插入中间路点来绕避拥堵区域。\n" +
               "你输出的路点将直接传入A*路径规划算法作为途经点，路点必须是上海市区真实道路附近的坐标（精确到小数点后3位）。\n" +
               "判断规则：\n" +
               "  - speedRatio < 0.7（当前比平时慢30%以上）且有慢速热点 → 考虑绕路（1-2个路点）\n" +
               "  - speedRatio >= 0.7 或历史样本不足（<10次）→ 直达策略（waypoints为空数组）\n" +
               "  - 路点不得在建筑内部或水域中，不得绕远超过直线距离的50%\n" +
               "你必须严格以JSON格式输出，不得包含任何JSON以外的文字。";
    }

    private String buildUserPrompt(double startLat, double startLon,
                                    double endLat, double endLon,
                                    HistoryContextDTO history) {
        LocalDateTime now = LocalDateTime.now();
        double straightDistKm = haversine(startLat, startLon, endLat, endLon) / 1000.0;

        StringBuilder sb = new StringBuilder();

        sb.append("## 规划任务\n");
        sb.append("起点坐标：(").append(startLat).append(", ").append(startLon).append(")\n");
        sb.append("终点坐标：(").append(endLat).append(", ").append(endLon).append(")\n");
        sb.append("直线距离：").append(String.format("%.2f", straightDistKm)).append(" km\n");
        sb.append("当前时间：").append(history.getDayOfWeek())
          .append(" ").append(String.format("%02d:%02d", now.getHour(), now.getMinute()))
          .append(" [").append(history.getTimePeriod()).append("]\n\n");

        sb.append("## 历史交通数据\n");
        sb.append("当前时段(").append(history.getCurrentHour()).append(":00)历史均速：")
          .append(String.format("%.1f", history.getCurrentHourAvgSpeedKmh())).append(" km/h\n");
        sb.append("全天历史均速：").append(String.format("%.1f", history.getAllDayAvgSpeedKmh())).append(" km/h\n");
        sb.append("速度比值(speedRatio)：").append(String.format("%.2f", history.getSpeedRatio()))
          .append("（低于0.7=严重拥堵，0.7-0.9=轻度拥堵，≥1.0=畅通）\n");
        sb.append("当前时段历史平均延误：").append(String.format("%.1f", history.getCurrentHourDelayMin())).append("分钟\n");
        sb.append("历史数据样本量：").append(history.getSpeedSampleCount()).append("次\n");

        if (!history.getSlowZones().isEmpty()) {
            sb.append("\n历史慢速热点区域（过去7天，均速<8km/h）：\n");
            for (HistoryContextDTO.SlowZone zone : history.getSlowZones()) {
                sb.append("  - 坐标(").append(zone.getLat()).append(",").append(zone.getLon()).append(")")
                  .append("  均速：").append(String.format("%.1f", zone.getAvgSpeedKmh())).append("km/h")
                  .append("  出现：").append(zone.getOccurrences()).append("次")
                  .append("  高峰时段：").append(zone.getPeakHour()).append(":00\n");
            }
        } else {
            sb.append("\n历史慢速热点：无记录\n");
        }
        sb.append("近7天起点附近异常路线：").append(history.getExceptionCount()).append("次\n");

        sb.append("\n## 输出格式（严格JSON）\n");
        sb.append("{\n");
        sb.append("  \"strategy\": \"<直达 | 绕行北侧 | 绕行南侧 | 绕行外环 | 其他描述>\",\n");
        sb.append("  \"reasoning\": \"<决策依据，引用具体历史数据，不超过60字>\",\n");
        sb.append("  \"waypoints\": [\n");
        sb.append("    {\"lat\": <纬度，精确到0.001>, \"lon\": <经度，精确到0.001>, \"label\": \"<路点说明>\"}\n");
        sb.append("  ],\n");
        sb.append("  \"expectedSpeedKmh\": <预估本次全程平均时速（数字）>,\n");
        sb.append("  \"confidenceLevel\": \"<HIGH=样本>100 | MEDIUM=10到100 | LOW=<10>\",\n");
        sb.append("  \"warnings\": [\"<注意事项>\"],\n");
        sb.append("  \"summary\": \"<给用户看的一句话，如：绕行北横通道，预计需X分钟>\"\n");
        sb.append("}");

        return sb.toString();
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
