package com.fm.logistics.service;

import com.fm.logistics.dto.RouteResultDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface RoutePlanningService {

    /**
     * 规划单段路线（起点 → 终点）
     * @param plannedTime 计划发货时间（null 则取当前时间），透传给 LLM 策略以选取对应时段历史数据
     */
    RouteResultDTO planRoute(double startLat, double startLon, double endLat, double endLon,
                             LocalDateTime plannedTime);

    /**
     * 规划多停靠路线（依次经过 waypoints 列表中所有路点）
     * @param waypoints   路点列表，每个元素为 [lat, lng]，第0个=起点，最后一个=终点
     * @param plannedTime 计划发货时间
     */
    RouteResultDTO planMultiStop(List<double[]> waypoints, LocalDateTime plannedTime);
}
