package com.fm.logistics.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fm.logistics.client.DeepSeekClient;
import com.fm.logistics.dto.CalibrationLlmContext;
import com.fm.logistics.dto.EdgeCostCalibrationDTO;
import com.fm.logistics.entity.HubLink;
import com.fm.logistics.service.LlmEdgeCostCalibrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM 切入点一：动态校准边费用
 * 参照 LlmJudgeRouteStrategy 的 Prompt 构建模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmEdgeCostCalibrationServiceImpl implements LlmEdgeCostCalibrationService {

    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<EdgeCostCalibrationDTO> calibrate(List<HubLink> links, LocalDate planDate,
                                                     CalibrationLlmContext ctx) {
        if (ctx == null) {
            ctx = new CalibrationLlmContext(null, "（暂无）", "（暂无）");
        }
        try {
            // 每条边约 40～80 token，边多时必须加大 max_tokens，否则输出被截断 → JSON 损坏 → 全体降级
            int maxOut = Math.min(8192, Math.max(2048, links.size() * 48 + 256));
            String json = deepSeekClient.callApi(
                    buildSystemPrompt(),
                    buildUserPrompt(links, planDate, ctx),
                    maxOut, true);
            return parseResponse(json, links);
        } catch (Exception e) {
            log.warn("[LLM费用校准] 调用失败，全部降级为倍率1.0: {}", e.getMessage());
            return buildFallback(links);
        }
    }

    private String buildSystemPrompt() {
        return "你是物流调度助手，只做一件事：为每条干线链路输出「费用倍率」。\n"
                + "【情境】用户消息首行「今日:yyyy-MM-dd」即决策日（星期、节假日语境按该日理解）。"
                + "其后若干段为天气、各边流量与容量等参考，请结合参考与链路列表综合判断；不要说明这些参考对应哪一日或来自何处。\n"
                + "【倍率含义】每条链路已有「基准费用（元/件）」。你要输出 multiplier（浮点数，无量纲）："
                + "系统将把该链路实际费用系数设为 基准 × multiplier。"
                + "1.0=维持基准；大于1=更拥堵/更不利/成本上调；小于1=更顺畅/成本可下调。请在 [0.7,2.0] 内取值。\n"
                + "【输出要求】只输出一个 JSON 对象，不要 Markdown、不要代码围栏、不要任何前后说明文字。格式严格为：\n"
                + "{\"calibrations\":[{\"linkId\":<整数>,\"multiplier\":<0.7~2.0>,\"reason\":\"<一两句中文>\"}, ...]}\n"
                + "calibrations 必须包含用户消息「链路」中列出的每一个 linkId，且每个 linkId 恰好出现一次；"
                + "reason 简要说明为何上调/下调/保持（可提及天气或某边负载，勿冗长）。";
    }

    /**
     * {@code ctx.llmReferenceDate} 非空则作为模型眼中的「今日」，否则用 {@code planDate}。
     * 天气与负载文案由上游按真实数据生成，此处不标注真实取数日期。
     */
    private String buildUserPrompt(List<HubLink> links, LocalDate planDate, CalibrationLlmContext ctx) {
        LocalDate todayForLlm = ctx.getLlmReferenceDate() != null ? ctx.getLlmReferenceDate() : planDate;
        StringBuilder sb = new StringBuilder();
        sb.append("今日:").append(todayForLlm).append("\n");
        sb.append(ctx.getWeatherText()).append("\n");
        sb.append(ctx.getYesterdayEdgesText()).append("\n");
        sb.append("请为下列每条链路给出 multiplier（JSON 对象字段 calibrations，格式见系统说明）。\n");
        sb.append("链路（共").append(links.size()).append("条，输出须覆盖全部 linkId）：\n");
        for (HubLink link : links) {
            sb.append(String.format("linkId=%d Hub%d→Hub%d %s 基准%.2f元/件\n",
                    link.getId(), link.getFromHubId(), link.getToHubId(),
                    link.getTransportMode(), link.getBaseCostPerUnit().doubleValue()));
        }
        return sb.toString();
    }

    private List<EdgeCostCalibrationDTO> parseResponse(String json, List<HubLink> links) throws Exception {
        String content = deepSeekClient.unwrapAssistantContent(json).trim();
        if (content.isEmpty()) {
            throw new RuntimeException("LLM返回为空");
        }
        JsonNode root = objectMapper.readTree(content);
        JsonNode arr = extractCalibrationsArray(root);
        if (arr == null || !arr.isArray()) {
            throw new RuntimeException("LLM返回中未找到 calibrations 数组");
        }

        Map<Long, EdgeCostCalibrationDTO> byLinkId = new LinkedHashMap<>();
        for (JsonNode node : arr) {
            if (!node.isObject()) {
                continue;
            }
            long linkId = node.path("linkId").asLong(0);
            if (linkId <= 0) {
                continue;
            }
            EdgeCostCalibrationDTO dto = new EdgeCostCalibrationDTO();
            dto.setLinkId(linkId);
            double m = node.path("multiplier").asDouble(1.0);
            dto.setMultiplier(Math.max(0.7, Math.min(2.0, m)));
            dto.setReason(node.path("reason").asText(""));
            dto.setLlmEnhanced(true);
            byLinkId.put(linkId, dto);
        }

        List<EdgeCostCalibrationDTO> result = new ArrayList<>();
        for (HubLink link : links) {
            EdgeCostCalibrationDTO got = byLinkId.get(link.getId());
            if (got != null) {
                result.add(got);
            } else {
                EdgeCostCalibrationDTO dto = new EdgeCostCalibrationDTO();
                dto.setLinkId(link.getId());
                dto.setMultiplier(1.0);
                dto.setReason("模型输出未包含该链路，保持基准倍率");
                dto.setLlmEnhanced(false);
                result.add(dto);
            }
        }
        return result;
    }

    /** 兼容 {"calibrations":[...]} 及历史根数组、其它常见键名 */
    private static JsonNode extractCalibrationsArray(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        String[] keys = {"calibrations", "items", "edges", "results", "data", "links"};
        for (String k : keys) {
            if (root.has(k) && root.get(k).isArray()) {
                return root.get(k);
            }
        }
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            JsonNode v = it.next().getValue();
            if (v != null && v.isArray()) {
                return v;
            }
        }
        return null;
    }

    private List<EdgeCostCalibrationDTO> buildFallback(List<HubLink> links) {
        return links.stream().map(link -> {
            EdgeCostCalibrationDTO dto = new EdgeCostCalibrationDTO();
            dto.setLinkId(link.getId());
            dto.setMultiplier(1.0);
            dto.setReason("LLM不可用，使用基准费用");
            dto.setLlmEnhanced(false);
            return dto;
        }).collect(Collectors.toList());
    }
}
