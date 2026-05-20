package com.fm.logistics.dto;

import com.fm.logistics.entity.LogisticsHub;
import lombok.Data;

import java.util.List;

/**
 * 智能 Hub 选择结果 DTO（方案C：LLM综合多因素选Hub）
 *
 * LLM 综合考虑：距离远近、今日负载、当前路段历史交通速度，
 * 从候选 Hub 中选出最优的一个。
 */
@Data
public class HubSelectionResultDTO {

    /** 最终选中的中转站实体 */
    private LogisticsHub selectedHub;

    /** LLM 给出的选择理由（中文，80字以内） */
    private String reason;

    /** LLM 决策置信度：HIGH | MEDIUM | LOW */
    private String confidenceLevel;

    /** 所有候选 Hub 的详细分析，供前端展示 */
    private List<HubCandidateInfo> candidates;

    /** 是否成功使用 LLM（false = 降级为纯距离最近） */
    private Boolean llmEnhanced;

    // ── 静态工厂方法 ──────────────────────────────────────────────────

    public static HubSelectionResultDTO noHub(String reason) {
        HubSelectionResultDTO r = new HubSelectionResultDTO();
        r.setSelectedHub(null);
        r.setReason(reason);
        r.setLlmEnhanced(false);
        r.setConfidenceLevel("LOW");
        return r;
    }

    public static HubSelectionResultDTO directSelect(LogisticsHub hub, String reason) {
        HubSelectionResultDTO r = new HubSelectionResultDTO();
        r.setSelectedHub(hub);
        r.setReason(reason);
        r.setLlmEnhanced(false);
        r.setConfidenceLevel("HIGH");
        return r;
    }

    // ── 候选 Hub 信息内部类 ────────────────────────────────────────────

    /**
     * 单个候选 Hub 的综合评分信息
     */
    @Data
    public static class HubCandidateInfo {

        /** Hub ID */
        private Long hubId;

        /** Hub 名称 */
        private String hubName;

        /** Hub 地址 */
        private String hubAddress;

        /** 到订单群中心的直线距离（km） */
        private double distanceKm;

        /** 今日已处理的批次数（负载指标） */
        private int todayBatchCount;

        /** 当前时段交通状况描述，如"交通顺畅（历史均速28km/h）" */
        private String trafficStatus;

        /** 当前时段历史均速（km/h） */
        private double avgSpeedKmh;
    }
}
