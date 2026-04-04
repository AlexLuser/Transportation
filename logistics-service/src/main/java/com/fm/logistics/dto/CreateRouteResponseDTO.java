package com.fm.logistics.dto;

import com.fm.logistics.entity.LogisticsRoute;
import lombok.Data;

/**
 * 创建物流路线接口响应：持久化后的路线 + 本次规划附带的 LLM 元数据（不入库）。
 */
@Data
public class CreateRouteResponseDTO {

    private LogisticsRoute route;

    /** true 表示本次结果经过 LLM 策略增强；幂等命中已有路线时为 false */
    private boolean llmEnhanced;

    private LlmDecisionResult llmDecision;

    public static CreateRouteResponseDTO of(LogisticsRoute route, RouteResultDTO planResult) {
        CreateRouteResponseDTO dto = new CreateRouteResponseDTO();
        dto.setRoute(route);
        if (planResult != null && planResult.isSuccess()) {
            dto.setLlmEnhanced(planResult.isLlmEnhanced());
            dto.setLlmDecision(planResult.getLlmDecision());
        } else {
            dto.setLlmEnhanced(false);
            dto.setLlmDecision(null);
        }
        return dto;
    }

    /** 订单已有路线或并发下命中已存在路线：无本次规划元数据 */
    public static CreateRouteResponseDTO existingRoute(LogisticsRoute route) {
        CreateRouteResponseDTO dto = new CreateRouteResponseDTO();
        dto.setRoute(route);
        dto.setLlmEnhanced(false);
        dto.setLlmDecision(null);
        return dto;
    }
}
