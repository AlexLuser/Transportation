package com.fm.logistics.dto;

import com.fm.logistics.entity.LogisticsBatch;
import com.fm.logistics.entity.LogisticsBatchItem;
import lombok.Data;

import java.util.List;

/**
 * 配送批次详情 DTO
 */
@Data
public class BatchDetailDTO {

    /** 批次基本信息 */
    private LogisticsBatch batch;

    /** Hub 信息 */
    private HubDTO hub;

    /** 干线路线详情（仓库→Hub，segment_type=1） */
    private RouteDetailDTO trunkRoute;

    /** 末端路线列表（Hub→各客户，segment_type=2） */
    private List<RouteDetailDTO> lastMileRoutes;

    /** 批次明细列表（含VRP访问顺序） */
    private List<LogisticsBatchItem> items;

    /** VRP 规划结果摘要 */
    private VrpResultDTO vrpResult;

    // ── 方案C：LLM智能Hub选择结果 ────────────────────────────────────

    /** LLM 选择 Hub 的理由（来自 LlmHubSelectionService，非持久化） */
    private String hubSelectionReason;

    /** LLM 选 Hub 置信度：HIGH | MEDIUM | LOW */
    private String hubSelectionConfidence;

    /** 是否使用了 LLM 进行 Hub 选择（false=降级为距离最近） */
    private Boolean hubSelectionLlmEnhanced;

    /** 所有候选 Hub 的详细分析（供前端展示对比） */
    private List<HubSelectionResultDTO.HubCandidateInfo> hubCandidates;
}
