package com.fm.logistics.service;

import com.fm.logistics.dto.CalibrationLlmContext;
import com.fm.logistics.dto.EdgeCostCalibrationDTO;
import com.fm.logistics.entity.HubLink;

import java.time.LocalDate;
import java.util.List;

/**
 * LLM 切入点一：运行前动态校准边费用
 */
public interface LlmEdgeCostCalibrationService {

    /**
     * 为每条 hub_link 生成今日有效费用倍率
     * LLM 调用失败时，全部降级为 multiplier=1.0
     *
     * @param planDate   业务规划日；当 llmContext 未指定模拟「今日」时，用作模型提示首行日期
     * @param llmContext 天气与负载摘要（后端按真实日期取数，提示词不写明来源）；可选模拟「今日」覆盖首行日期
     */
    List<EdgeCostCalibrationDTO> calibrate(List<HubLink> links, LocalDate planDate, CalibrationLlmContext llmContext);
}
