package com.fm.logistics.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * AI 自然语言查询响应 DTO（预留大模型接口）
 */
@Data
@Schema(description = "AI自然语言查询响应（预留大模型接口）")
public class AINLPQueryResponseDTO {

    @Schema(description = "自然语言回答（直接展示给用户）")
    private String answer;

    @Schema(description = "数据类型：ROUTE_STATUS=路线状态, CURRENT_LOCATION=当前位置, " +
            "ETA=预计到达时间, TRACK_HISTORY=轨迹历史, DISPATCH_OVERVIEW=调度概览, UNKNOWN=未知")
    private String dataType;

    @Schema(description = "结构化数据（对应 dataType 的原始数据，供前端渲染）")
    private Object structuredData;

    @Schema(description = "附加属性（扩展字段）")
    private Map<String, Object> extraInfo;

    @Schema(description = "建议的后续问题（提升交互体验）")
    private List<String> followUpSuggestions;
}

