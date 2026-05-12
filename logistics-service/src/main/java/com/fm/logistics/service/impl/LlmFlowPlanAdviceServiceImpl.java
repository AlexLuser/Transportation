package com.fm.logistics.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fm.logistics.client.DeepSeekClient;
import com.fm.logistics.dto.FlowPlanAdviceDTO;
import com.fm.logistics.entity.FlowPlanItem;
import com.fm.logistics.entity.HubLink;
import com.fm.logistics.entity.NationalHub;
import com.fm.logistics.service.LlmFlowPlanAdviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM 切入点二：流量规划结果分析
 * 参照 GlobalDispatchAdviceServiceImpl 的 Prompt 构建模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmFlowPlanAdviceServiceImpl implements LlmFlowPlanAdviceService {

    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    @Override
    public FlowPlanAdviceDTO advise(List<FlowPlanItem> items, List<NationalHub> hubs, List<HubLink> links) {
        try {
            Map<Long, HubLink> linkMap = links.stream()
                    .collect(Collectors.toMap(HubLink::getId, l -> l));
            Map<Long, NationalHub> hubMap = hubs.stream()
                    .collect(Collectors.toMap(NationalHub::getId, h -> h));

            String json = deepSeekClient.callApi(
                    buildSystemPrompt(),
                    buildUserPrompt(items, linkMap, hubMap),
                    1536,
                    true,
                    0.52);
            return parseResponse(json);
        } catch (Exception e) {
            log.warn("[LLM规划顾问] 调用失败，返回空建议: {}", e.getMessage());
            return buildFallback();
        }
    }

    private String buildSystemPrompt() {
        return "你是资深全国干线网络运营顾问。输入是一次多商品最小费用流（MCMF）求得的边流与成本快照——数字已由算法算好，"
                + "你的价值不在于用固定阈值重算一遍「谁超 80%、谁占 30%」，而在于像真人复盘一样做结构与策略层面的解读。\n"
                + "请综合思考（可择要展开，不必面面俱到）：走廊/枢纽是否过度集中、是否存在隐性单点或绕行、成本结构是否失衡、"
                + "若次日需求波动哪些边最先承压、有哪些「算法目标里没写但现场会在意」的风险或机会。\n"
                + "允许合理推断与优先级判断；若信息不足，在文字中点明假设即可，勿编造具体数字。\n"
                + "输出必须是单个 JSON 对象（勿 Markdown、勿代码围栏），字段：\n"
                + "{\"bottleneckAnalysis\":\"…\",\"costAnomalyWarning\":\"…\",\"suggestions\":[\"…\",\"…\"],\"summary\":\"…\"}\n"
                + "bottleneckAnalysis：偏网络与运力视角的段落；costAnomalyWarning：偏成本集中度/异常形态（不必等于「占比告警」）；"
                + "suggestions：3～6 条可执行或可供决策的讨论点，按重要性大致排序；summary：给管理层的两三句话总览。";
    }

    private String buildUserPrompt(List<FlowPlanItem> items,
                                   Map<Long, HubLink> linkMap,
                                   Map<Long, NationalHub> hubMap) {
        double totalCost = items.stream()
                .mapToDouble(i -> i.getTotalCost().doubleValue()).sum();
        int totalFlow = items.stream().mapToInt(FlowPlanItem::getFlowAmount).sum();
        int activeEdges = (int) items.stream().filter(i -> i.getFlowAmount() > 0).count();
        double maxLoadPct = 0;
        for (FlowPlanItem item : items) {
            HubLink link = linkMap.get(item.getLinkId());
            if (link == null || link.getCapacityDaily() <= 0) {
                continue;
            }
            double lr = (double) item.getFlowAmount() / link.getCapacityDaily() * 100;
            if (lr > maxLoadPct) {
                maxLoadPct = lr;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下为本次 MCMF 求解后的干线边流明细。请先把握整体画像，再落到关键边与结构问题。\n");
        sb.append(String.format("整体：总流量=%d件，总成本=%.2f元，有流量的边=%d条，观测到的最高容量负载率=%.0f%%（供结构判断，勿机械当阈值）。\n\n",
                totalFlow, totalCost, activeEdges, maxLoadPct));
        sb.append("边明细（名称/件数/容量/负载率/成本与占比）：\n");
        for (FlowPlanItem item : items) {
            HubLink link = linkMap.get(item.getLinkId());
            if (link == null) {
                continue;
            }
            NationalHub from = hubMap.get(item.getFromHubId());
            NationalHub to = hubMap.get(item.getToHubId());
            double loadRate = link.getCapacityDaily() > 0
                    ? (double) item.getFlowAmount() / link.getCapacityDaily() * 100 : 0;
            double costShare = totalCost > 0
                    ? item.getTotalCost().doubleValue() / totalCost * 100 : 0;
            sb.append(String.format("  %s→%s: 流量=%d件, 容量=%d件, 负载率=%.0f%%, 小计=%.2f元(占比%.0f%%)\n",
                    from != null ? from.getName() : "?",
                    to != null ? to.getName() : "?",
                    item.getFlowAmount(), link.getCapacityDaily(),
                    loadRate, item.getTotalCost().doubleValue(), costShare));
        }
        return sb.toString();
    }

    /**
     * DeepSeekClient 已解包为 {@code message.content} 字符串；此处同时兼容传入整段 Chat Completions JSON。
     */
    private FlowPlanAdviceDTO parseResponse(String raw) throws Exception {
        String content = deepSeekClient.unwrapAssistantContent(raw);
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new RuntimeException("LLM返回格式异常：未找到可解析的 JSON 对象");
        }
        String jsonObj = content.substring(start, end + 1);
        JsonNode node = objectMapper.readTree(jsonObj);

        FlowPlanAdviceDTO dto = new FlowPlanAdviceDTO();
        dto.setBottleneckAnalysis(node.path("bottleneckAnalysis").asText(""));
        dto.setCostAnomalyWarning(node.path("costAnomalyWarning").asText(""));
        List<String> suggestions = new ArrayList<>();
        JsonNode sug = node.path("suggestions");
        if (sug.isArray()) {
            sug.forEach(s -> suggestions.add(s.asText()));
        }
        dto.setSuggestions(suggestions);
        dto.setSummary(node.path("summary").asText(""));
        dto.setLlmEnhanced(true);
        return dto;
    }

    private FlowPlanAdviceDTO buildFallback() {
        FlowPlanAdviceDTO dto = new FlowPlanAdviceDTO();
        dto.setBottleneckAnalysis("");
        dto.setCostAnomalyWarning("");
        dto.setSuggestions(Collections.emptyList());
        dto.setSummary("");
        dto.setLlmEnhanced(false);
        return dto;
    }
}
