package com.fm.logistics.service;

import com.fm.logistics.dto.FlowPlanDetailDTO;
import com.fm.logistics.entity.FlowPlan;

import java.time.LocalDate;

public interface FlowPlanService {

    /**
     * 手动触发：规划→落库→生成干线批次（同步返回结果）
     *
     * @param planDate          MCMF 与订单统计业务日
     * @param llmReferenceDate  可选，费用校准模型提示中的「今日」；为空则用 planDate。不影响订单统计与天气/负载取数（仍按真实昨日等）
     */
    FlowPlanDetailDTO triggerManually(LocalDate planDate, LocalDate llmReferenceDate);

    /** 查询最新完成的规划单详情 */
    FlowPlanDetailDTO getLatest();

    /** 查询指定规划单详情 */
    FlowPlanDetailDTO getById(Long id);
}
