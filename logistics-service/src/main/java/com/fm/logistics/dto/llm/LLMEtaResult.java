package com.fm.logistics.dto.llm;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * LLM 动态 ETA 预测结果
 * 每隔 N 次 GPS 上报，由 LLMService.predictETA() 返回并写入 logistics_route
 */
@Data
public class LLMEtaResult {

    /** 最新预测的到达时间 */
    private Date predictedArrivalTime;

    /** 距到达还需多少分钟 */
    private int remainingMinutes;

    /** 延误风险：0=准时 1=可能延误 2=大概率延误 */
    private int delayRisk;

    /** 延误原因（LLM 分析，例如"当前速度低于历史均值 30%"） */
    private List<String> delayReasons;

    /** 给司机/调度员的建议 */
    private List<String> recommendations;

    /** 是否为降级结果 */
    private boolean fallback;
}

