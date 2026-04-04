package com.fm.logistics.service;

import com.fm.logistics.dto.HistoryContextDTO;

/**
 * 路径规划历史数据服务
 * 聚合 logistics_track 和 logistics_route 两张表的历史统计数据
 * 作为 LLM 决策的背景上下文
 */
public interface RouteHistoryService {

    /**
     * 构建历史数据上下文
     *
     * @param startLat 起点纬度
     * @param startLon 起点经度
     * @param endLat   终点纬度
     * @param endLon   终点经度
     * @return 聚合后的历史统计信息
     */
    HistoryContextDTO buildContext(double startLat, double startLon,
                                   double endLat, double endLon);
}
