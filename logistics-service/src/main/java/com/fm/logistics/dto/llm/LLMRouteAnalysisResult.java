package com.fm.logistics.dto.llm;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * LLM 路线分析结果
 * 由 LLMServiceImpl 解析 LLM 的 JSON 输出后填充
 */
@Data
public class LLMRouteAnalysisResult {

    /** 预估配送时长（分钟），LLM 基于历史数据+当前条件给出 */
    private int estimatedDurationMinutes;

    /** 预计到达时间 */
    private Date estimatedArrivalTime;

    /** 风险等级：0=正常 1=轻微风险 2=高风险 */
    private int riskLevel;

    /** 风险因素描述列表，例如 ["晚高峰路段", "目标区域历史延误率较高"] */
    private List<String> riskFactors;

    /** 路线建议（自然语言，可直接展示给司机/调度员） */
    private String recommendation;

    /** LLM 原始响应 JSON 字符串（调试用，可选存储） */
    private String rawResponse;

    /** 是否为降级结果（LLM 不可用时为 true） */
    private boolean fallback;
}

