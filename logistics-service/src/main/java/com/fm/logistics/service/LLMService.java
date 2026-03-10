package com.fm.logistics.service;

import com.fm.logistics.dto.llm.HistoricalStats;
import com.fm.logistics.dto.llm.LLMEtaResult;
import com.fm.logistics.dto.llm.LLMRouteAnalysisResult;
import com.fm.logistics.entity.LogisticsRoute;

/**
 * 大模型（LLM）集成服务
 *
 * 设计原则：
 *   1. LLM 调用仅在 Service 层触发，Controller 无感知
 *   2. 所有方法均有降级策略（llm.enabled=false 或调用失败时用规则兜底）
 *   3. 兼容任何 OpenAI Chat Completions API 格式的模型
 *
 * 核心能力：
 *   - analyzeRoute()    : 创建路线时，结合历史统计数据给出 ETA 和风险评估
 *   - predictETA()      : GPS 上报时动态更新 ETA（考虑当前速度趋势和历史同期数据）
 *   - analyzeAnomaly()  : 检测到异常时用自然语言解释原因和建议措施
 */
public interface LLMService {

    /**
     * 路线创建时的首次分析
     * 触发时机：订单状态变为"已发货"，LogisticsRouteServiceImpl.createRoute() 调用
     *
     * @param route       已创建的路线实体（含出发地、目的地、坐标）
     * @param historical  HistoricalAnalysisService 预先聚合的历史统计
     * @return 分析结果（含 estimatedDurationMinutes、riskLevel、recommendation）
     */
    LLMRouteAnalysisResult analyzeRoute(LogisticsRoute route, HistoricalStats historical);

    /**
     * GPS 上报时的动态 ETA 重算
     * 触发时机：LogisticsTrackServiceImpl.uploadLocation() 中，每 N 次上报触发一次
     *
     * @param route             当前路线
     * @param currentLat        最新纬度
     * @param currentLng        最新经度
     * @param currentSpeedKmh   当前速度（km/h）
     * @param elapsedMinutes    已配送用时（分钟）
     * @param remainingDistKm   剩余距离估算（km），调用方基于直线距离估算
     * @param historical        历史统计（复用 analyzeByDestination 结果）
     * @return ETA 预测结果
     */
    LLMEtaResult predictETA(LogisticsRoute route,
                             Double currentLat,
                             Double currentLng,
                             double currentSpeedKmh,
                             int elapsedMinutes,
                             double remainingDistKm,
                             HistoricalStats historical);

    /**
     * 异常路线的 LLM 解释
     * 触发时机：LogisticsAIServiceImpl.checkAnomaly() 检测出异常后调用
     *
     * @param route          当前路线
     * @param anomalyDesc    规则检测到的异常描述（例如"停滞30分钟"）
     * @param historical     历史统计
     * @return 自然语言解释和建议措施字符串
     */
    String explainAnomaly(LogisticsRoute route, String anomalyDesc, HistoricalStats historical);
}

