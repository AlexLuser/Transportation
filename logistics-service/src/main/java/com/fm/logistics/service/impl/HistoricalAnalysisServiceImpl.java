package com.fm.logistics.service.impl;

import com.fm.logistics.dto.llm.HistoricalStats;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.mapper.LogisticsRouteMapper;
import com.fm.logistics.mapper.LogisticsTrackMapper;
import com.fm.logistics.service.HistoricalAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HistoricalAnalysisServiceImpl implements HistoricalAnalysisService {

    @Autowired
    private LogisticsRouteMapper routeMapper;

    @Autowired
    private LogisticsTrackMapper trackMapper;

    @Override
    public HistoricalStats analyzeByDestination(String endAddress, int departHour) {
        HistoricalStats stats = new HistoricalStats();
        stats.setAvgDurationByHour(-1);
        stats.setRecentAvgSpeed(0);

        // 1. 提取城市关键词（城市 > 区）
        String cityKeyword = extractCityKeyword(endAddress);

        // 2. 查询整体历史统计
        Map<String, Object> overall = routeMapper.selectHistoricalStats(cityKeyword);
        if (overall == null || toInt(overall.get("sample_count")) == 0) {
            stats.setSampleCount(0);
            stats.setSummaryText("该目的地暂无历史配送数据。");
            return stats;
        }

        int sampleCount = toInt(overall.get("sample_count"));
        double avgDur    = toDouble(overall.get("avg_duration_min"));
        double maxDur    = toDouble(overall.get("max_duration_min"));
        double minDur    = toDouble(overall.get("min_duration_min"));
        int delayCount   = toInt(overall.get("delay_count"));
        double delayRate = sampleCount > 0 ? (double) delayCount / sampleCount : 0;

        stats.setSampleCount(sampleCount);
        stats.setAvgDurationMinutes(avgDur);
        stats.setMaxDurationMinutes(maxDur);
        stats.setMinDurationMinutes(minDur);
        stats.setDelayRate(delayRate);

        // 3. 计算当前出发时段的历史时长（时段特征分析）
        List<Map<String, Object>> recentList = routeMapper.selectRecentDurations(cityKeyword);
        if (!recentList.isEmpty()) {
            // 同出发时段（±1小时）的样本
            double samePeriodTotal = 0;
            int samePeriodCount = 0;
            for (Map<String, Object> row : recentList) {
                int hour = toInt(row.get("depart_hour"));
                if (Math.abs(hour - departHour) <= 1) {
                    samePeriodTotal += toDouble(row.get("duration_min"));
                    samePeriodCount++;
                }
            }
            if (samePeriodCount > 0) {
                stats.setAvgDurationByHour(samePeriodTotal / samePeriodCount);
            }
        }

        // 4. 生成描述性文字（直接嵌入 Prompt）
        stats.setSummaryText(buildSummaryText(stats, departHour));
        return stats;
    }

    @Override
    public HistoricalStats analyzeRouteProgress(LogisticsRoute route,
                                                  int elapsedMinutes,
                                                  double progressRatio) {
        int departHour = 12; // 默认
        if (route.getCreateTime() != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(route.getCreateTime());
            departHour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        }

        HistoricalStats stats = analyzeByDestination(route.getEndAddress(), departHour);

        // 额外计算当前路线的近期平均速度
        Map<String, Object> speedStats = trackMapper.selectSpeedStats(route.getId(), 20);
        if (speedStats != null && speedStats.get("avg_speed") != null) {
            stats.setRecentAvgSpeed(toDouble(speedStats.get("avg_speed")));
        }

        // 补充进度诊断
        if (stats.getSampleCount() > 0 && stats.getAvgDurationMinutes() > 0) {
            double expectedElapsed = stats.getAvgDurationMinutes() * progressRatio;
            double ratio = elapsedMinutes / expectedElapsed;
            String progressNote;
            if (ratio < 0.85) {
                progressNote = "当前进度明显快于历史均值（已完成 " + pct(progressRatio)
                        + "，用时仅历史均值的 " + pct(ratio) + "）。";
            } else if (ratio > 1.3) {
                progressNote = "当前进度慢于历史均值（已完成 " + pct(progressRatio)
                        + "，用时已达历史均值的 " + pct(ratio) + "，存在延误风险）。";
            } else {
                progressNote = "当前进度与历史均值基本一致（已完成 " + pct(progressRatio) + "）。";
            }
            stats.setSummaryText(stats.getSummaryText() + " " + progressNote);
        }
        return stats;
    }

    // ---------- 工具方法 ----------

    /**
     * 从地址字符串提取城市关键词
     * 优先取"XX市"，无市则取"XX区/XX县"
     */
    private String extractCityKeyword(String address) {
        if (address == null || address.isBlank()) return "";
        // 尝试提取"XX市"
        Matcher cityMatcher = Pattern.compile("([\u4e00-\u9fa5]{2,5}市)").matcher(address);
        if (cityMatcher.find()) {
            return cityMatcher.group(1);
        }
        // 尝试提取"XX区"或"XX县"
        Matcher districtMatcher = Pattern.compile("([\u4e00-\u9fa5]{2,4}[区县])").matcher(address);
        if (districtMatcher.find()) {
            return districtMatcher.group(1);
        }
        // fallback：取地址前 4 个字
        return address.substring(0, Math.min(4, address.length()));
    }

    private String buildSummaryText(HistoricalStats s, int departHour) {
        if (s.getSampleCount() == 0) {
            return "该目的地暂无历史配送数据，无法提供参考。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("历史参考（%d条记录）：", s.getSampleCount()));
        sb.append(String.format("平均配送时长 %.0f 分钟", s.getAvgDurationMinutes()));
        sb.append(String.format("（最短 %.0f 分钟，最长 %.0f 分钟）", s.getMinDurationMinutes(), s.getMaxDurationMinutes()));
        sb.append(String.format("，历史延误率 %.1f%%", s.getDelayRate() * 100));

        if (s.getAvgDurationByHour() > 0) {
            String period = departHour < 9 ? "早高峰" : (departHour < 12 ? "上午" :
                    (departHour < 14 ? "午间" : (departHour < 18 ? "下午" :
                            (departHour < 21 ? "晚高峰" : "夜间"))));
            sb.append(String.format("。%s时段（%d时附近）历史均值约 %.0f 分钟",
                    period, departHour, s.getAvgDurationByHour()));
        }
        sb.append("。");
        return sb.toString();
    }

    private String pct(double val) {
        return String.format("%.0f%%", val * 100);
    }

    private int toInt(Object obj) {
        if (obj == null) return 0;
        return ((Number) obj).intValue();
    }

    private double toDouble(Object obj) {
        if (obj == null) return 0;
        return ((Number) obj).doubleValue();
    }
}

