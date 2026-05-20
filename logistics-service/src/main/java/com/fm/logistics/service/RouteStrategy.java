package com.fm.logistics.service;

import com.fm.logistics.dto.GeoJsonLineString;
import com.fm.logistics.dto.RouteResultDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 路径规划策略接口
 *
 * 设计意图（策略模式）：
 *   将"用什么算法规划路径"这个决策从业务逻辑中解耦出来。
 *   当前实现：AStarRouteStrategy / LlmJudgeRouteStrategy / LlmWaypointRouteStrategy
 *
 * 切换策略时，业务层（RoutePlanningService）代码无需改动，
 * 只需在 Spring 配置中指定注入哪个实现类即可。
 */
public interface RouteStrategy {

    /**
     * 规划从起点到终点的路线（单段）
     *
     * @param startLat    起点纬度
     * @param startLon    起点经度
     * @param endLat      终点纬度
     * @param endLon      终点经度
     * @param plannedTime 计划发货时间（null 则取当前时间），LLM 策略用此选取对应时段历史数据
     * @return 规划结果，包含 GeoJSON 路线、距离、预计时间
     */
    RouteResultDTO plan(double startLat, double startLon,
                        double endLat,   double endLon,
                        LocalDateTime plannedTime);

    /**
     * 规划经过多个路点的路线（多段，Hub-and-Spoke 用）
     *
     * 默认实现：将多个路点依次两两调用 plan()，将各段 GeoJSON coordinates 首尾相接合并。
     * 各子策略可覆盖此方法以提供更智能的多段规划（如 LLM 统一选优）。
     *
     * @param waypoints   路点列表，每个元素 = [lat, lng]，第0个=起点，最后一个=终点
     * @param plannedTime 计划发货时间
     * @return 合并后的完整路线规划结果
     */
    default RouteResultDTO planMultiStop(List<double[]> waypoints, LocalDateTime plannedTime) {
        if (waypoints == null || waypoints.size() < 2) {
            return RouteResultDTO.error("路点数量不足，至少需要起点和终点");
        }
        if (waypoints.size() == 2) {
            return plan(waypoints.get(0)[0], waypoints.get(0)[1],
                        waypoints.get(1)[0], waypoints.get(1)[1], plannedTime);
        }

        // 依次规划相邻路段，合并 GeoJSON coordinates
        GeoJsonLineString mergedPoints = new GeoJsonLineString();
        double totalDistance = 0;
        long totalDuration = 0;
        boolean anyLlmEnhanced = false;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            double[] from = waypoints.get(i);
            double[] to   = waypoints.get(i + 1);
            RouteResultDTO segResult = plan(from[0], from[1], to[0], to[1], plannedTime);
            if (!segResult.isSuccess()) {
                return RouteResultDTO.error("第" + (i + 1) + "段路线规划失败: " + segResult.getErrorMsg());
            }
            totalDistance += segResult.getDistanceMeters();
            totalDuration += segResult.getDurationMs();
            if (segResult.isLlmEnhanced()) anyLlmEnhanced = true;

            // 合并坐标：除第一段外，跳过第一个点（与上一段末尾重复）
            // GeoJsonLineString 内部存储格式为 [lng, lat]，addPoint(lat, lng) 会自动交换
            List<double[]> coords = segResult.getRoutePoints().getCoordinates();
            int startIdx = (i == 0) ? 0 : 1;
            for (int j = startIdx; j < coords.size(); j++) {
                // coords[j] = [lng, lat]，addPoint 参数顺序是 (lat, lng)
                mergedPoints.addPoint(coords.get(j)[1], coords.get(j)[0]);
            }
        }

        RouteResultDTO merged = RouteResultDTO.success(totalDistance, totalDuration, mergedPoints);
        merged.setLlmEnhanced(anyLlmEnhanced);
        return merged;
    }

    /**
     * 策略名称，用于日志和监控区分当前使用的是哪种算法
     */
    String strategyName();
}