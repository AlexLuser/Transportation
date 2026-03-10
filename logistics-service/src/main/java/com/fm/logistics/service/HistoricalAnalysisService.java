package com.fm.logistics.service;

import com.fm.logistics.dto.llm.HistoricalStats;
import com.fm.logistics.entity.LogisticsRoute;

/**
 * 历史数据分析服务
 * 在调用 LLM 之前，先从数据库聚合计算历史统计数据，
 * 为 Prompt 提供结构化的历史背景信息。
 */
public interface HistoricalAnalysisService {

    /**
     * 根据目标地址（城市/区域关键词）提取历史配送统计
     *
     * @param endAddress 收货地址（全文），从中提取城市/区关键词
     * @param departHour 出发时段（0-23），用于时段相关性分析
     * @return 历史统计结果
     */
    HistoricalStats analyzeByDestination(String endAddress, int departHour);

    /**
     * 分析某条路线当前的运行质量（与历史比较）
     * 用于 ETA 更新时向 LLM 描述"当前进度是否正常"
     *
     * @param route         当前路线
     * @param elapsedMinutes 已耗时（分钟）
     * @param progressRatio  已完成比例（0.0~1.0，基于距离估算）
     * @return 历史统计（包含同类路线在相同进度下的速度期望）
     */
    HistoricalStats analyzeRouteProgress(LogisticsRoute route,
                                          int elapsedMinutes,
                                          double progressRatio);
}

