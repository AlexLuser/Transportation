package com.fm.logistics.service;

import com.fm.logistics.dto.ai.*;

/**
 * 物流 AI 对外服务接口（手动触发类接口）
 *
 * 说明：
 *   ETA 预测已由 LLMService 在 GPS 上报时自动触发，不再作为独立接口暴露。
 *   本接口仅保留需要管理员/用户手动触发的 AI 功能。
 */
public interface LogisticsAIService {

    /**
     * 手动触发路线重新分析（管理员调用，比如路况突变时重算路线建议）
     * 内部调用 LLMService 并将结果写入 logistics_route.ai_suggested_route
     */
    AIRouteSuggestResponseDTO suggestRoute(AIRouteSuggestRequestDTO request);

    /**
     * 路线异常检测与 LLM 解释
     * 先用规则检测超时/停车/信号丢失，再用 LLM 生成自然语言告警和建议措施
     */
    AIAnomalyCheckResponseDTO checkAnomaly(Long routeId);

    /**
     * 智能调度优化（预留）
     */
    AIDispatchOptimizeResponseDTO optimizeDispatch(AIDispatchOptimizeRequestDTO request);

    /**
     * 自然语言查询（预留）
     */
    AINLPQueryResponseDTO nlpQuery(AINLPQueryRequestDTO request);
}

