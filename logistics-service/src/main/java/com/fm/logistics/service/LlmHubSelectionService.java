package com.fm.logistics.service;

import com.fm.logistics.dto.HubSelectionResultDTO;

import java.time.LocalDateTime;

/**
 * LLM 智能 Hub 选择服务（方案C）
 *
 * 比简单"选最近Hub"更智能：综合考虑
 *   - 各 Hub 到订单群的距离
 *   - 各 Hub 今日已处理批次数（负载）
 *   - 各 Hub 路段当前历史交通速度（高峰期 vs 平峰期）
 * 由 DeepSeek LLM 综合分析，选出最优中转站。
 *
 * 由 LogisticsBatchServiceImpl.createBatch() 在选 Hub 时调用（替代原 findNearestHub）
 */
public interface LlmHubSelectionService {

    /**
     * 智能选择最优中转站
     *
     * @param centerLat   订单群中心纬度
     * @param centerLng   订单群中心经度
     * @param orderCount  本批次订单数量（影响 Hub 容量判断）
     * @param plannedTime 计划发车时间（null 则取当前时间）
     * @return Hub 选择结果（含选中的 Hub、理由、所有候选分析）
     */
    HubSelectionResultDTO selectHub(double centerLat,
                                    double centerLng,
                                    int orderCount,
                                    LocalDateTime plannedTime);
}
