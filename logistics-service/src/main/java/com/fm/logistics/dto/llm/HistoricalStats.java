package com.fm.logistics.dto.llm;

import lombok.Data;

/**
 * 历史配送数据统计结果（喂给 LLM Prompt 用）
 * 由 HistoricalAnalysisService 从 logistics_route 表聚合计算
 */
@Data
public class HistoricalStats {

    /** 历史样本数量（越多越可信） */
    private int sampleCount;

    /** 历史平均配送时长（分钟） */
    private double avgDurationMinutes;

    /** 历史最短配送时长（分钟） */
    private double minDurationMinutes;

    /** 历史最长配送时长（分钟） */
    private double maxDurationMinutes;

    /** 历史延误率（超出预计 15 分钟以上的比例，0.0~1.0） */
    private double delayRate;

    /** 当前出发时段（0-23小时）的历史平均时长（分钟，-1表示无数据） */
    private double avgDurationByHour;

    /** 最近 N 次轨迹的平均速度（km/h，0 表示无数据） */
    private double recentAvgSpeed;

    /** 描述性文字（由分析服务生成，直接嵌入 Prompt） */
    private String summaryText;
}

