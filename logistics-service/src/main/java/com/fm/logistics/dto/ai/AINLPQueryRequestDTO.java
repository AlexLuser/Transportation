package com.fm.logistics.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Map;

/**
 * AI 自然语言查询请求 DTO（预留大模型接口）
 * 用户用自然语言提问物流相关问题，由大模型解析后返回结构化答案
 *
 * 示例：
 *   - "我的订单现在到哪里了？"
 *   - "这个快递还有多久能到？"
 *   - "今天有哪些单子还没派出去？"
 */
@Data
@Schema(description = "AI自然语言查询请求（预留大模型接口）")
public class AINLPQueryRequestDTO {

    @Schema(description = "自然语言问题", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "我的订单现在到哪里了？")
    private String query;

    @Schema(description = "当前用户ID")
    private Long userId;

    @Schema(description = "用户角色：customer=买家, driver=运输员, shop=商户, admin=管理员")
    private String userRole;

    @Schema(description = "上下文参数（如 orderId, routeId 等，帮助大模型精确理解问题）")
    private Map<String, Object> context;
}

