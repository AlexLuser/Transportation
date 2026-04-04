package com.fm.logistics.dto;

import lombok.Data;
import java.util.List;

/**
 * 历史数据上下文 DTO
 * 聚合从 logistics_track 和 logistics_route 表提取的历史统计信息
 * 作为 LLM 决策时的背景数据
 */
@Data
public class HistoryContextDTO {

    /** 当前小时（0-23） */
    private int currentHour;

    /** 当前星期（中文，如"星期一"） */
    private String dayOfWeek;

    /** 当前时段描述（早高峰/午间高峰/晚高峰/平峰） */
    private String timePeriod;

    /** 当前小时段过去30天历史平均行驶速度（km/h），无数据时默认35.0 */
    private double currentHourAvgSpeedKmh;

    /** 全天过去30天历史平均行驶速度（km/h），无数据时默认35.0 */
    private double allDayAvgSpeedKmh;

    /**
     * 速度比值 = 当前时段均速 / 全天均速
     * < 1.0 说明当前比平时慢（拥堵）；> 1.0 说明比平时快（畅通）
     */
    private double speedRatio;

    /** 当前时段速度数据的样本量（影响 LLM 置信度判断） */
    private int speedSampleCount;

    /** 当前小时段过去30天历史平均延误分钟数 */
    private double currentHourDelayMin;

    /** 全天过去30天历史平均延误分钟数 */
    private double allDayDelayMin;

    /** 起终点中心区域历史慢速热点列表（均速 < 8km/h，出现 ≥ 3次） */
    private List<SlowZone> slowZones;

    /** 近7天起点附近异常路线数量（routeStatus=3） */
    private int exceptionCount;

    /** 最近一次异常时间描述（格式化字符串，无记录时为 null） */
    private String lastExceptionDesc;

    @Data
    public static class SlowZone {
        /** 热点中心纬度（精度0.01度，约1km网格） */
        private double lat;
        /** 热点中心经度 */
        private double lon;
        /** 该区域历史平均速度（km/h） */
        private double avgSpeedKmh;
        /** 过去7天出现次数 */
        private int occurrences;
        /** 最频繁出现的小时段 */
        private int peakHour;
    }
}
