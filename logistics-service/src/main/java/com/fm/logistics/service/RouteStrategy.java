package com.fm.logistics.service;

import com.fm.logistics.dto.RouteResultDTO;

/**
 * 路径规划策略接口
 *
 * 设计意图（策略模式）：
 *   将"用什么算法规划路径"这个决策从业务逻辑中解耦出来。
 *   当前实现：AStarRouteStrategy（自实现 A* 算法）
 *
 * 切换策略时，业务层（RoutePlanningService）代码无需改动，
 * 只需在 Spring 配置中指定注入哪个实现类即可。
 */
public interface RouteStrategy {

    /**
     * 规划从起点到终点的路线
     *
     * @param startLat 起点纬度
     * @param startLon 起点经度
     * @param endLat   终点纬度
     * @param endLon   终点经度
     * @return 规划结果，包含 GeoJSON 路线、距离、预计时间
     */
    RouteResultDTO plan(double startLat, double startLon,
                        double endLat,   double endLon);

    /**
     * 策略名称，用于日志和监控区分当前使用的是哪种算法
     * 示例：返回 "A_STAR" 或 "LLM_ENHANCED"
     */
    String strategyName();
}