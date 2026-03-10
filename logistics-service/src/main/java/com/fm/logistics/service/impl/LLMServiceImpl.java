package com.fm.logistics.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fm.logistics.config.LLMProperties;
import com.fm.logistics.dto.llm.HistoricalStats;
import com.fm.logistics.dto.llm.LLMEtaResult;
import com.fm.logistics.dto.llm.LLMRouteAnalysisResult;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.service.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 大模型集成服务实现
 *
 * 技术方案：
 *   - 使用 RestTemplate 调用 OpenAI Chat Completions API（/v1/chat/completions）
 *   - 兼容所有支持该格式的模型（通义千问、DeepSeek、文心一言、本地 Ollama 等）
 *   - 要求 LLM 输出严格的 JSON（通过 system prompt 约束 + response_format 参数）
 *   - 所有方法包含 try-catch 降级：LLM 不可用时自动切换为规则计算
 *
 * Prompt 设计思路：
 *   1. System Role：限定身份（专业物流AI助手）+ 输出格式约束（必须输出指定 JSON）
 *   2. User Message：喂入结构化数据（历史统计 + 当前状态 + 环境信息）
 *   3. 低温度参数（0.2~0.4）确保输出格式稳定，减少 JSON 解析失败
 */
@Service
public class LLMServiceImpl implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMServiceImpl.class);

    @Autowired
    private LLMProperties llmProps;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ============================================================
    //  1. 路线首次分析
    // ============================================================

    @Override
    public LLMRouteAnalysisResult analyzeRoute(LogisticsRoute route, HistoricalStats historical) {
        if (!llmProps.isEnabled()) {
            log.info("[LLM] 大模型未启用，使用规则降级计算路线分析");
            return fallbackRouteAnalysis(route, historical);
        }
        try {
            String systemPrompt = buildRouteSystemPrompt();
            String userPrompt   = buildRouteUserPrompt(route, historical);

            String rawJson = callLLM(systemPrompt, userPrompt);
            return parseRouteAnalysisResult(rawJson, route, historical);

        } catch (Exception e) {
            log.warn("[LLM] 路线分析调用失败，降级到规则计算: {}", e.getMessage());
            return fallbackRouteAnalysis(route, historical);
        }
    }

    // ============================================================
    //  2. 动态 ETA 预测
    // ============================================================

    @Override
    public LLMEtaResult predictETA(LogisticsRoute route,
                                    Double currentLat,
                                    Double currentLng,
                                    double currentSpeedKmh,
                                    int elapsedMinutes,
                                    double remainingDistKm,
                                    HistoricalStats historical) {
        if (!llmProps.isEnabled()) {
            log.debug("[LLM] 大模型未启用，使用规则降级计算 ETA");
            return fallbackEta(remainingDistKm, currentSpeedKmh);
        }
        try {
            String systemPrompt = buildEtaSystemPrompt();
            String userPrompt   = buildEtaUserPrompt(route, currentSpeedKmh, elapsedMinutes,
                                                      remainingDistKm, historical);
            String rawJson = callLLM(systemPrompt, userPrompt);
            return parseEtaResult(rawJson, remainingDistKm, currentSpeedKmh);

        } catch (Exception e) {
            log.warn("[LLM] ETA 预测调用失败，降级到规则计算: {}", e.getMessage());
            return fallbackEta(remainingDistKm, currentSpeedKmh);
        }
    }

    // ============================================================
    //  3. 异常解释
    // ============================================================

    @Override
    public String explainAnomaly(LogisticsRoute route, String anomalyDesc, HistoricalStats historical) {
        if (!llmProps.isEnabled()) {
            return "检测到异常：" + anomalyDesc + "。建议联系司机确认情况。";
        }
        try {
            String systemPrompt = "你是专业的物流调度助手。用简洁中文（100字以内）分析异常原因并给出处置建议。";
            String userPrompt = String.format(
                    "路线信息：从「%s」到「%s」\n" +
                    "检测到异常：%s\n" +
                    "%s\n" +
                    "请分析可能原因并给出具体处置建议。",
                    route.getStartAddress(), route.getEndAddress(),
                    anomalyDesc,
                    historical.getSummaryText());

            return callLLM(systemPrompt, userPrompt);

        } catch (Exception e) {
            log.warn("[LLM] 异常解释调用失败: {}", e.getMessage());
            return "检测到异常：" + anomalyDesc + "。建议联系司机确认情况。";
        }
    }

    // ============================================================
    //  核心：HTTP 调用 LLM API
    // ============================================================

    /**
     * 调用 OpenAI 兼容的 Chat Completions API
     * 返回第一个 choice 的 message.content 文本
     */
    private String callLLM(String systemPrompt, String userPrompt) throws Exception {
        String url = llmProps.getBaseUrl().replaceAll("/$", "") + "/v1/chat/completions";

        // 构建请求体
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", llmProps.getModel());
        requestBody.put("temperature", llmProps.getTemperature());
        requestBody.put("max_tokens", llmProps.getMaxTokens());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user",   "content", userPrompt));
        requestBody.put("messages", messages);

        // 要求 JSON 输出（部分模型支持 response_format；Ollama 等不支持时设 json-mode-enabled: false）
        if (llmProps.isJsonModeEnabled()) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (llmProps.getApiKey() != null && !llmProps.getApiKey().isBlank()) {
            headers.setBearerAuth(llmProps.getApiKey());
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        log.debug("[LLM] 调用 {} 模型: {}", llmProps.getModel(), url);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("LLM API 返回非 2xx: " + response.getStatusCode());
        }

        // 解析 choices[0].message.content
        JsonNode root    = objectMapper.readTree(response.getBody());
        JsonNode content = root.path("choices").get(0).path("message").path("content");
        if (content.isMissingNode()) {
            throw new RuntimeException("LLM 响应缺少 choices[0].message.content");
        }
        return content.asText();
    }

    // ============================================================
    //  Prompt 构建：路线分析
    // ============================================================

    private String buildRouteSystemPrompt() {
        return "你是一名专业的物流配送AI分析师。\n" +
               "任务：根据用户提供的路线信息和历史配送数据，评估本次配送的时长和风险。\n" +
               "输出要求：必须返回严格的 JSON，字段如下，不得包含任何 Markdown 或额外说明：\n" +
               "{\n" +
               "  \"estimatedDurationMinutes\": <整数，预估配送时长（分钟）>,\n" +
               "  \"riskLevel\": <0=正常|1=轻微风险|2=高风险>,\n" +
               "  \"riskFactors\": [\"风险因素1\", \"风险因素2\"],\n" +
               "  \"recommendation\": \"给司机的路线建议（50字以内）\"\n" +
               "}";
    }

    private String buildRouteUserPrompt(LogisticsRoute route, HistoricalStats historical) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String timePeriod = hour < 7 ? "凌晨" : hour < 9 ? "早高峰" : hour < 12 ? "上午" :
                            hour < 14 ? "午间" : hour < 17 ? "下午" : hour < 19 ? "晚高峰" : "夜间";

        // 直线距离估算（仅作参考，LLM 不依赖精确距离）
        double distKm = estimateDistKm(route.getStartLat(), route.getStartLng(),
                                        route.getEndLat(), route.getEndLng());

        return String.format(
                "【配送任务信息】\n" +
                "- 出发地：%s\n" +
                "- 目的地：%s\n" +
                "- 出发坐标：纬度 %s，经度 %s\n" +
                "- 目的坐标：纬度 %s，经度 %s\n" +
                "- 直线距离：约 %.1f km\n" +
                "- 当前时段：%s（北京时间 %d 时）\n" +
                "\n【历史配送数据参考】\n%s\n" +
                "\n请综合以上信息（时段特征、历史延误率、路程距离）给出分析。",
                nvl(route.getStartAddress()), nvl(route.getEndAddress()),
                nvl(route.getStartLat()),     nvl(route.getStartLng()),
                nvl(route.getEndLat()),       nvl(route.getEndLng()),
                distKm, timePeriod, hour,
                historical.getSummaryText());
    }

    // ============================================================
    //  Prompt 构建：ETA 动态预测
    // ============================================================

    private String buildEtaSystemPrompt() {
        return "你是专业的物流 ETA 预测 AI。\n" +
               "任务：根据配送当前进度和历史数据，预测最新的到达时间和延误风险。\n" +
               "输出要求：必须返回严格的 JSON，字段如下：\n" +
               "{\n" +
               "  \"remainingMinutes\": <整数，预计还需多少分钟到达>,\n" +
               "  \"delayRisk\": <0=准时|1=可能延误|2=大概率延误>,\n" +
               "  \"delayReasons\": [\"原因1\", \"原因2\"],\n" +
               "  \"recommendations\": [\"建议1\", \"建议2\"]\n" +
               "}";
    }

    private String buildEtaUserPrompt(LogisticsRoute route,
                                       double currentSpeedKmh,
                                       int elapsedMinutes,
                                       double remainingDistKm,
                                       HistoricalStats historical) {
        // 计算进度比例
        double totalDistKm = estimateDistKm(route.getStartLat(), route.getStartLng(),
                                             route.getEndLat(), route.getEndLng());
        double progressRatio = totalDistKm > 0
                ? (totalDistKm - remainingDistKm) / totalDistKm : 0;

        // 速度评估（与历史均值比较）
        String speedNote = "";
        if (historical.getAvgDurationMinutes() > 0 && totalDistKm > 0) {
            double historicalAvgSpeed = totalDistKm / (historical.getAvgDurationMinutes() / 60.0);
            double speedRatio = currentSpeedKmh / historicalAvgSpeed;
            speedNote = String.format("当前速度 %.1f km/h，历史同类路线均速约 %.1f km/h（当前为历史的 %.0f%%）。",
                    currentSpeedKmh, historicalAvgSpeed, speedRatio * 100);
        }

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        return String.format(
                "【当前配送进度】\n" +
                "- 路线：%s → %s\n" +
                "- 已用时：%d 分钟，已完成：%.0f%%\n" +
                "- 剩余距离：约 %.1f km\n" +
                "- 当前速度：%.1f km/h\n" +
                "- 当前时段：北京时间 %d 时\n" +
                "%s\n" +
                "\n【历史参考】\n%s\n" +
                "\n请结合当前进度速度趋势和历史数据，预测最新的到达时间和延误风险。",
                nvl(route.getStartAddress()), nvl(route.getEndAddress()),
                elapsedMinutes, progressRatio * 100,
                remainingDistKm, currentSpeedKmh, hour,
                speedNote, historical.getSummaryText());
    }

    // ============================================================
    //  解析 LLM 输出
    // ============================================================

    private LLMRouteAnalysisResult parseRouteAnalysisResult(String rawJson,
                                                              LogisticsRoute route,
                                                              HistoricalStats historical) {
        LLMRouteAnalysisResult result = new LLMRouteAnalysisResult();
        result.setRawResponse(rawJson);
        try {
            JsonNode node = objectMapper.readTree(rawJson);

            int durationMin = node.path("estimatedDurationMinutes").asInt(0);
            if (durationMin <= 0) {
                // 降级：用历史均值
                durationMin = (int) Math.max(historical.getAvgDurationMinutes(), 30);
            }
            result.setEstimatedDurationMinutes(durationMin);
            result.setEstimatedArrivalTime(minutesFromNow(durationMin));
            result.setRiskLevel(node.path("riskLevel").asInt(0));

            List<String> riskFactors = new ArrayList<>();
            node.path("riskFactors").forEach(n -> riskFactors.add(n.asText()));
            result.setRiskFactors(riskFactors);
            result.setRecommendation(node.path("recommendation").asText(""));
            result.setFallback(false);
            log.info("[LLM] 路线分析完成：预估 {} 分钟，风险等级 {}", durationMin, result.getRiskLevel());

        } catch (Exception e) {
            log.warn("[LLM] 解析路线分析结果失败: {}，rawJson={}", e.getMessage(), rawJson);
            return fallbackRouteAnalysis(route, historical);
        }
        return result;
    }

    private LLMEtaResult parseEtaResult(String rawJson,
                                         double remainingDistKm,
                                         double currentSpeedKmh) {
        LLMEtaResult result = new LLMEtaResult();
        try {
            JsonNode node = objectMapper.readTree(rawJson);

            int remainingMin = node.path("remainingMinutes").asInt(0);
            if (remainingMin <= 0) {
                remainingMin = ruleBasedRemainingMin(remainingDistKm, currentSpeedKmh);
            }
            result.setRemainingMinutes(remainingMin);
            result.setPredictedArrivalTime(minutesFromNow(remainingMin));
            result.setDelayRisk(node.path("delayRisk").asInt(0));

            List<String> reasons = new ArrayList<>();
            node.path("delayReasons").forEach(n -> reasons.add(n.asText()));
            result.setDelayReasons(reasons);

            List<String> recs = new ArrayList<>();
            node.path("recommendations").forEach(n -> recs.add(n.asText()));
            result.setRecommendations(recs);
            result.setFallback(false);
            log.debug("[LLM] ETA 预测完成：还需 {} 分钟，延误风险 {}", remainingMin, result.getDelayRisk());

        } catch (Exception e) {
            log.warn("[LLM] 解析 ETA 结果失败: {}", e.getMessage());
            return fallbackEta(remainingDistKm, currentSpeedKmh);
        }
        return result;
    }

    // ============================================================
    //  降级策略（规则计算）
    // ============================================================

    /**
     * LLM 不可用时的路线分析降级：基于历史均值 + 时段系数
     */
    private LLMRouteAnalysisResult fallbackRouteAnalysis(LogisticsRoute route,
                                                           HistoricalStats historical) {
        LLMRouteAnalysisResult result = new LLMRouteAnalysisResult();

        // 基础时长：历史均值 > 距离估算 > 默认 60 分钟
        int durationMin;
        double distKm = estimateDistKm(route.getStartLat(), route.getStartLng(),
                                        route.getEndLat(), route.getEndLng());
        if (historical.getAvgDurationByHour() > 0) {
            durationMin = (int) historical.getAvgDurationByHour();
        } else if (historical.getAvgDurationMinutes() > 0) {
            durationMin = (int) historical.getAvgDurationMinutes();
        } else if (distKm > 0) {
            // 假设城区平均 30 km/h，郊区 50 km/h
            durationMin = distKm < 20 ? (int) (distKm / 30.0 * 60) : (int) (distKm / 50.0 * 60);
        } else {
            durationMin = 60;
        }

        // 时段系数（高峰期增加 30%）
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if ((hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19)) {
            durationMin = (int) (durationMin * 1.3);
        }

        result.setEstimatedDurationMinutes(durationMin);
        result.setEstimatedArrivalTime(minutesFromNow(durationMin));
        result.setRiskLevel(historical.getDelayRate() > 0.3 ? 1 : 0);
        result.setRiskFactors(Collections.emptyList());
        result.setRecommendation("（规则计算，未启用AI分析）请注意交通状况，按时送达。");
        result.setFallback(true);
        return result;
    }

    /**
     * LLM 不可用时的 ETA 降级：distance / speed
     */
    private LLMEtaResult fallbackEta(double remainingDistKm, double currentSpeedKmh) {
        LLMEtaResult result = new LLMEtaResult();
        int remainingMin = ruleBasedRemainingMin(remainingDistKm, currentSpeedKmh);
        result.setRemainingMinutes(remainingMin);
        result.setPredictedArrivalTime(minutesFromNow(remainingMin));
        result.setDelayRisk(0);
        result.setDelayReasons(Collections.emptyList());
        result.setRecommendations(Collections.emptyList());
        result.setFallback(true);
        return result;
    }

    private int ruleBasedRemainingMin(double remainingDistKm, double currentSpeedKmh) {
        if (currentSpeedKmh > 0) {
            return (int) (remainingDistKm / currentSpeedKmh * 60);
        }
        // 速度为 0 时假设 30 km/h
        return remainingDistKm > 0 ? (int) (remainingDistKm / 30.0 * 60) : 30;
    }

    // ============================================================
    //  工具方法
    // ============================================================

    /** Haversine 公式计算两点直线距离（km） */
    private double estimateDistKm(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return 0;
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1))
                 * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Date minutesFromNow(int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }

    private String nvl(Object obj) {
        return obj == null ? "未知" : obj.toString();
    }
}

