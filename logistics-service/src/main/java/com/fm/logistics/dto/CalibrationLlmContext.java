package com.fm.logistics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 费用校准 LLM 上下文：天气与干线负载在后端仍按<strong>真实日期</strong>取数，写入模型的字符串不交代来源；
 * {@code llmReferenceDate} 非空时，模型提示中的「今日」用该日，否则用业务 {@code planDate}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalibrationLlmContext {

    /** 可选；费用校准模型眼中的「今日」（模拟日）；为空则由调用方在提示中使用规划日 */
    private LocalDate llmReferenceDate;

    /** 各边流量与负载等摘要（面向模型的中性表述） */
    private String yesterdayEdgesText;

    /** 代表城市天气摘要（面向模型、不含具体日期） */
    private String weatherText;
}
