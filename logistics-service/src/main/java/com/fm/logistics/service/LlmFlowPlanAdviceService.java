package com.fm.logistics.service;

import com.fm.logistics.dto.FlowPlanAdviceDTO;
import com.fm.logistics.entity.FlowPlanItem;
import com.fm.logistics.entity.HubLink;
import com.fm.logistics.entity.NationalHub;

import java.util.List;

/**
 * LLM 切入点二：规划结果解读与风险分析
 */
public interface LlmFlowPlanAdviceService {

    /**
     * 分析 MCMF 流量分配结果，识别瓶颈、成本异常、单点风险
     * LLM 失败时返回空建议，不影响主流程
     */
    FlowPlanAdviceDTO advise(List<FlowPlanItem> items, List<NationalHub> hubs, List<HubLink> links);
}
