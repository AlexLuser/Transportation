package com.fm.logistics.service.impl;

import com.fm.logistics.dto.ai.*;
import com.fm.logistics.dto.llm.HistoricalStats;
import com.fm.logistics.dto.llm.LLMRouteAnalysisResult;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.mapper.LogisticsRouteMapper;
import com.fm.logistics.mapper.LogisticsTrackMapper;
import com.fm.logistics.service.HistoricalAnalysisService;
import com.fm.logistics.service.LLMService;
import com.fm.logistics.service.LogisticsAIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 物流 AI 服务实现
 *
 * ETA 预测已从本类移出，由 LogisticsTrackServiceImpl 在 GPS 上报时自动触发（每 N 次重算一次）。
 * 本类专注于：
 *   1. 手动触发路线重分析（suggestRoute）：聚合历史数据 + 调用 LLMService.analyzeRoute()
 *   2. 异常检测（checkAnomaly）：规则引擎检测 + LLMService.explainAnomaly() 生成自然语言解释
 *   3. 调度优化和 NLP 查询（预留）
 */
@Service
public class LogisticsAIServiceImpl implements LogisticsAIService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsAIServiceImpl.class);

    @Autowired
    private LogisticsRouteMapper routeMapper;

    @Autowired
    private LogisticsTrackMapper trackMapper;

    @Autowired
    private LLMService llmService;

    @Autowired
    private HistoricalAnalysisService historicalAnalysisService;

    /**
     * 手动触发路线分析（管理员/调度员使用，比如路况突变时重算）
     * 流程：聚合历史数据 → 构建 Prompt → 调用 LLM → 将结果写回 logistics_route
     */
    @Override
    public AIRouteSuggestResponseDTO suggestRoute(AIRouteSuggestRequestDTO request) {
        AIRouteSuggestResponseDTO response = new AIRouteSuggestResponseDTO();

        // 1. 查询路线（通过 routeId 或 orderId）
        LogisticsRoute route = null;
        if (request.getRouteId() != null) {
            route = routeMapper.selectById(request.getRouteId());
        }
        if (route == null) {
            response.setSuggestBy("ERROR");
            response.setAnalysisNote("路线不存在，请提供有效的 routeId");
            response.setRiskFactors(Collections.emptyList());
            response.setRouteNodes(Collections.emptyList());
            response.setAlternativeRoutes(Collections.emptyList());
            return response;
        }

        // 2. 聚合历史数据
        int departHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        HistoricalStats historical = historicalAnalysisService
                .analyzeByDestination(route.getEndAddress(), departHour);

        // 3. 调用 LLM 分析
        LLMRouteAnalysisResult result = llmService.analyzeRoute(route, historical);

        // 4. 将结果写回路线记录
        LogisticsRoute update = new LogisticsRoute();
        update.setId(route.getId());
        update.setEstimatedArrivalTime(result.getEstimatedArrivalTime());
        update.setAiAnalysis(buildAiAnalysisJson(result, historical));
        routeMapper.updateById(update);

        // 5. 组装响应
        response.setSuggestBy(result.isFallback() ? "RULE_FALLBACK" : "LLM");
        response.setConfidence(result.isFallback() ? 0.5 : 0.85);
        response.setAnalysisNote(result.getRecommendation());
        response.setRiskFactors(result.getRiskFactors() != null
                ? result.getRiskFactors() : Collections.emptyList());
        response.setRouteNodes(Collections.emptyList());   // GeoJSON 在 ai_analysis 字段
        response.setAlternativeRoutes(Collections.emptyList());
        log.info("[AI] 路线 {} 重分析完成：预计{}分钟，风险等级{}",
                route.getRouteNo(), result.getEstimatedDurationMinutes(), result.getRiskLevel());
        return response;
    }

    /**
     * 路线异常检测
     * 规则引擎检测超时/信号丢失/停滞，再用 LLM 生成自然语言解释和建议
     */
    @Override
    public AIAnomalyCheckResponseDTO checkAnomaly(Long routeId) {
        AIAnomalyCheckResponseDTO response = new AIAnomalyCheckResponseDTO();
        response.setRouteId(routeId);
        response.setAnomalies(new ArrayList<>());
        response.setOverallRiskScore(0);

        LogisticsRoute route = routeMapper.selectById(routeId);
        if (route == null) {
            response.setHasAnomaly(false);
            response.setOverallAssessment("路线不存在");
            return response;
        }

        List<AIAnomalyCheckResponseDTO.AnomalyItem> anomalies = new ArrayList<>();
        StringBuilder anomalyDesc = new StringBuilder();

        // ---------- 规则1：超时检测（超预计到达时间 2 小时仍未送达） ----------
        if (route.getEstimatedArrivalTime() != null && route.getRouteStatus() == 1) {
            long overdueMs = System.currentTimeMillis() - route.getEstimatedArrivalTime().getTime();
            if (overdueMs > 2 * 3600 * 1000L) {
                long overdueHours = overdueMs / 3600000;
                AIAnomalyCheckResponseDTO.AnomalyItem item = new AIAnomalyCheckResponseDTO.AnomalyItem();
                item.setAnomalyType("TIMEOUT");
                item.setSeverity(2);
                item.setDescription("已超过预计送达时间 " + overdueHours + " 小时");
                item.setSuggestedAction("联系运输员确认情况，通知买家延迟");
                item.setDetectedAt(new Date());
                anomalies.add(item);
                anomalyDesc.append("超时").append(overdueHours).append("小时；");
            }
        }

        // ---------- 规则2：信号丢失（超过 30 分钟未上报 GPS） ----------
        if (route.getLastTrackTime() != null && route.getRouteStatus() == 1) {
            long silentMs = System.currentTimeMillis() - route.getLastTrackTime().getTime();
            if (silentMs > 30 * 60 * 1000L) {
                long silentMin = silentMs / 60000;
                AIAnomalyCheckResponseDTO.AnomalyItem item = new AIAnomalyCheckResponseDTO.AnomalyItem();
                item.setAnomalyType("SIGNAL_LOST");
                item.setSeverity(silentMin > 60 ? 2 : 1);
                item.setDescription("运输员超过 " + silentMin + " 分钟未上报位置");
                item.setSuggestedAction("尝试联系运输员，确认是否正常行驶");
                item.setDetectedAt(new Date());
                anomalies.add(item);
                anomalyDesc.append("信号丢失").append(silentMin).append("分钟；");
            }
        }

        // ---------- 规则3：配送时长异常（超历史均值 2 倍） ----------
        if (route.getCreateTime() != null && route.getRouteStatus() == 1) {
            int departHour = 12;
            Calendar cal = Calendar.getInstance();
            cal.setTime(route.getCreateTime());
            departHour = cal.get(Calendar.HOUR_OF_DAY);

            HistoricalStats historical = historicalAnalysisService
                    .analyzeByDestination(route.getEndAddress(), departHour);

            if (historical.getSampleCount() >= 5 && historical.getAvgDurationMinutes() > 0) {
                long elapsedMin = (System.currentTimeMillis() - route.getCreateTime().getTime()) / 60_000;
                if (elapsedMin > historical.getAvgDurationMinutes() * 2) {
                    AIAnomalyCheckResponseDTO.AnomalyItem item = new AIAnomalyCheckResponseDTO.AnomalyItem();
                    item.setAnomalyType("EXCESSIVE_DURATION");
                    item.setSeverity(1);
                    item.setDescription(String.format("已用时 %d 分钟，超过历史均值（%.0f分钟）的 2 倍",
                            elapsedMin, historical.getAvgDurationMinutes()));
                    item.setSuggestedAction("建议确认是否遇到交通拥堵或其他阻碍");
                    item.setDetectedAt(new Date());
                    anomalies.add(item);
                    anomalyDesc.append("超均值2倍；");
                }
            }
        }

        // ---------- LLM 解释异常（若有异常则调用 LLM 生成自然语言分析） ----------
        String assessment;
        if (anomalies.isEmpty()) {
            assessment = "当前路线运行正常，未检测到异常。";
        } else {
            // 获取历史数据用于 LLM 上下文
            int departHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            HistoricalStats historical = historicalAnalysisService
                    .analyzeByDestination(route.getEndAddress(), departHour);

            String llmExplanation = llmService.explainAnomaly(
                    route, anomalyDesc.toString(), historical);
            assessment = "[AI分析] " + llmExplanation;
            log.info("[AI] 路线 {} 异常检测完成，LLM 解释：{}", routeId, llmExplanation);
        }

        response.setHasAnomaly(!anomalies.isEmpty());
        response.setAnomalies(anomalies);
        response.setOverallRiskScore(anomalies.stream()
                .mapToInt(AIAnomalyCheckResponseDTO.AnomalyItem::getSeverity).sum() * 10);
        response.setOverallAssessment(assessment);
        return response;
    }

    @Override
    public AIDispatchOptimizeResponseDTO optimizeDispatch(AIDispatchOptimizeRequestDTO request) {
        AIDispatchOptimizeResponseDTO response = new AIDispatchOptimizeResponseDTO();
        response.setAssignments(new ArrayList<>());
        response.setUnassignedRouteIds(new ArrayList<>());
        response.setOptimizationScore(0);
        response.setAnalysisNote("[预留接口] 智能调度优化：将待配送路线和可用司机信息传入 LLM，" +
                "通过 Function Calling 输出最优派单方案（VRP 问题）。");
        return response;
    }

    @Override
    public AINLPQueryResponseDTO nlpQuery(AINLPQueryRequestDTO request) {
        AINLPQueryResponseDTO response = new AINLPQueryResponseDTO();
        response.setDataType("PENDING");
        response.setAnswer("[预留接口] 自然语言查询：使用 RAG 方案检索相关物流数据，" +
                "构建 Prompt 让 LLM 生成自然语言回答（如：'我的包裹现在在哪里？'）。");
        response.setFollowUpSuggestions(Arrays.asList(
                "实时位置查询：GET /api/logistics/routes/order/{orderId}",
                "最新轨迹：GET /api/logistics/track/{routeId}/latest",
                "异常路线：GET /api/logistics/dispatch/anomaly"
        ));
        return response;
    }

    // ---------- 私有工具方法 ----------

    private String buildAiAnalysisJson(LLMRouteAnalysisResult result, HistoricalStats historical) {
        List<String> riskFactors = result.getRiskFactors() != null
                ? result.getRiskFactors() : Collections.emptyList();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"estimatedDurationMinutes\":").append(result.getEstimatedDurationMinutes()).append(",");
        sb.append("\"riskLevel\":").append(result.getRiskLevel()).append(",");
        sb.append("\"riskFactors\":").append(listToJson(riskFactors)).append(",");
        sb.append("\"recommendation\":\"").append(escapeJson(result.getRecommendation())).append("\",");
        sb.append("\"historicalSamples\":").append(historical.getSampleCount()).append(",");
        sb.append("\"historicalAvgMinutes\":").append((int) historical.getAvgDurationMinutes()).append(",");
        sb.append("\"historicalDelayRate\":").append(String.format("%.2f", historical.getDelayRate())).append(",");
        sb.append("\"isFallback\":").append(result.isFallback());
        sb.append("}");
        return sb.toString();
    }

    private String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
