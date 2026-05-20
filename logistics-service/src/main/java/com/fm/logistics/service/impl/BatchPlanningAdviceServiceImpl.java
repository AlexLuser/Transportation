package com.fm.logistics.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fm.logistics.client.DeepSeekClient;
import com.fm.logistics.dto.BatchAdviceDTO;
import com.fm.logistics.dto.CreateBatchRequestDTO;
import com.fm.logistics.entity.LogisticsHub;
import com.fm.logistics.mapper.LogisticsHubMapper;
import com.fm.logistics.service.BatchPlanningAdviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 批次配送策略顾问 — LLM 实现（方案A）
 *
 * 核心逻辑：
 *   ① 用 Haversine 公式估算"直送总里程"和"合并配送总里程"
 *   ② 将订单分布、里程对比、最近 Hub 信息组装为 Prompt
 *   ③ 调用 DeepSeek，解析 JSON 响应
 *   ④ LLM 失败时降级为规则判断（节省 > 10% 则推荐 Hub 模式）
 */
@Service
public class BatchPlanningAdviceServiceImpl implements BatchPlanningAdviceService {

    private static final Logger log = LoggerFactory.getLogger(BatchPlanningAdviceServiceImpl.class);

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private LogisticsHubMapper hubMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // ----------------------------------------------------------------

    @Override
    public BatchAdviceDTO advise(double warehouseLat, double warehouseLng,
                                  String warehouseAddress,
                                  List<CreateBatchRequestDTO.OrderItem> orderItems,
                                  LocalDateTime plannedTime) {

        // ── ① 计算订单群中心坐标 ────────────────────────────────────
        double centerLat = orderItems.stream()
                .mapToDouble(CreateBatchRequestDTO.OrderItem::getEndLat)
                .average().orElse(warehouseLat);
        double centerLng = orderItems.stream()
                .mapToDouble(CreateBatchRequestDTO.OrderItem::getEndLng)
                .average().orElse(warehouseLng);

        // ── ② 估算直送总里程（每辆车各自从仓库出发） ─────────────────
        double directTotal = orderItems.stream()
                .mapToDouble(item -> haversine(warehouseLat, warehouseLng,
                        item.getEndLat(), item.getEndLng()))
                .sum();

        // ── ③ 查最近 Hub，估算合并配送总里程 ──────────────────────────
        List<LogisticsHub> nearestHubs = hubMapper.selectNearestHubs(centerLat, centerLng, 1);
        double batchTotal = 0;
        if (!nearestHubs.isEmpty()) {
            LogisticsHub hub = nearestHubs.get(0);
            double trunkDist = haversine(warehouseLat, warehouseLng,
                    hub.getLatitude(), hub.getLongitude());
            double lastMileSum = orderItems.stream()
                    .mapToDouble(item -> haversine(hub.getLatitude(), hub.getLongitude(),
                            item.getEndLat(), item.getEndLng()))
                    .sum();
            batchTotal = trunkDist + lastMileSum;
        }

        // ── ④ 调用 LLM，失败则降级 ───────────────────────────────────
        try {
            String userPrompt = buildUserPrompt(
                    warehouseLat, warehouseLng, warehouseAddress,
                    orderItems, nearestHubs,
                    directTotal, batchTotal,
                    centerLat, centerLng, plannedTime);

            String llmResponse = deepSeekClient.callApi(buildSystemPrompt(), userPrompt);

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(llmResponse, Map.class);

            BatchAdviceDTO result = new BatchAdviceDTO();
            // useHub 字段可能是布尔或字符串，统一处理
            Object useHubRaw = parsed.get("useHub");
            result.setUseHub(Boolean.TRUE.equals(useHubRaw)
                    || "true".equalsIgnoreCase(String.valueOf(useHubRaw)));
            result.setStrategy(String.valueOf(parsed.getOrDefault("strategy", "HUB_AND_SPOKE")));
            result.setReason(String.valueOf(parsed.getOrDefault("reason", "")));
            result.setEstimatedSaving(String.valueOf(parsed.getOrDefault("estimatedSaving", "")));
            result.setUrgencyLevel(String.valueOf(parsed.getOrDefault("urgencyLevel", "MEDIUM")));
            result.setDispersionAnalysis(String.valueOf(parsed.getOrDefault("dispersionAnalysis", "")));
            result.setLlmEnhanced(true);
            result.setDirectTotalDistanceM(directTotal);
            result.setBatchTotalDistanceM(batchTotal > 0 ? batchTotal : null);

            log.info("[Advise] LLM建议: useHub={}, strategy={}", result.getUseHub(), result.getStrategy());
            return result;

        } catch (Exception e) {
            log.warn("[Advise] LLM调用失败，降级为规则判断: {}", e.getMessage());
            return buildFallbackAdvice(directTotal, batchTotal, orderItems.size());
        }
    }

    // ----------------------------------------------------------------
    //  Prompt 构建
    // ----------------------------------------------------------------

    private String buildSystemPrompt() {
        return "你是一位资深物流调度专家，专注于上海城市配送优化。" +
               "请根据订单分布和里程数据，分析最优的批次配送策略。" +
               "必须严格以 JSON 格式输出，不得添加任何 JSON 之外的说明文字。";
    }

    private String buildUserPrompt(double warehouseLat, double warehouseLng,
                                    String warehouseAddress,
                                    List<CreateBatchRequestDTO.OrderItem> orderItems,
                                    List<LogisticsHub> nearestHubs,
                                    double directTotal, double batchTotal,
                                    double centerLat, double centerLng,
                                    LocalDateTime plannedTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 批次配送策略分析请求\n\n");
        sb.append(String.format("**仓库**：%s（%.4f, %.4f）\n",
                warehouseAddress != null ? warehouseAddress : "上海仓库", warehouseLat, warehouseLng));
        sb.append(String.format("**订单数量**：%d 个\n", orderItems.size()));
        sb.append(String.format("**计划发车**：%s\n",
                plannedTime != null ? plannedTime.toString() : "立即出发"));
        sb.append(String.format("**订单群中心**：(%.4f, %.4f)\n\n", centerLat, centerLng));

        sb.append("**订单目的地列表**（序号. 地址，距仓库直线距离）：\n");
        for (int i = 0; i < orderItems.size(); i++) {
            CreateBatchRequestDTO.OrderItem item = orderItems.get(i);
            double d = haversine(warehouseLat, warehouseLng,
                    item.getEndLat(), item.getEndLng()) / 1000.0;
            sb.append(String.format("  %d. %s（%.2f km）\n", i + 1,
                    item.getEndAddress() != null ? item.getEndAddress() : "未知地址", d));
        }

        if (!nearestHubs.isEmpty()) {
            LogisticsHub hub = nearestHubs.get(0);
            double hubDist = haversine(centerLat, centerLng,
                    hub.getLatitude(), hub.getLongitude()) / 1000.0;
            sb.append(String.format("\n**最近可用中转站**：%s（距订单群中心 %.2f km）\n",
                    hub.getName(), hubDist));
        } else {
            sb.append("\n**中转站**：当前区域暂无可用中转站\n");
        }

        sb.append(String.format("\n**里程对比**：\n"));
        sb.append(String.format("  - 直送模式（各辆车独立出发）：估算总里程 %.2f km\n",
                directTotal / 1000.0));
        if (batchTotal > 0) {
            sb.append(String.format("  - Hub合并模式（干线+末端）：估算总里程 %.2f km\n",
                    batchTotal / 1000.0));
            double savePct = (directTotal - batchTotal) / directTotal * 100;
            sb.append(String.format("  - 节省估算：%.1f%%\n", savePct));
        }

        sb.append("\n请基于以上信息给出配送策略建议，**严格**按如下 JSON 格式输出：\n");
        sb.append("{\n");
        sb.append("  \"useHub\": true,\n");
        sb.append("  \"strategy\": \"HUB_AND_SPOKE\",\n");
        sb.append("  \"reason\": \"理由（100字以内）\",\n");
        sb.append("  \"estimatedSaving\": \"预计节省约XX%里程\",\n");
        sb.append("  \"urgencyLevel\": \"MEDIUM\",\n");
        sb.append("  \"dispersionAnalysis\": \"订单分散度简评（50字以内）\"\n");
        sb.append("}");

        return sb.toString();
    }

    // ----------------------------------------------------------------
    //  降级：规则判断（节省 >10% 则推荐 Hub 模式）
    // ----------------------------------------------------------------

    private BatchAdviceDTO buildFallbackAdvice(double directTotal, double batchTotal, int orderCount) {
        BatchAdviceDTO result = new BatchAdviceDTO();

        boolean hubRecommended = batchTotal > 0
                && ((directTotal - batchTotal) / directTotal) > 0.10;

        result.setUseHub(hubRecommended);
        result.setStrategy(hubRecommended ? "HUB_AND_SPOKE" : "DIRECT_MULTI_DROP");
        result.setLlmEnhanced(false);
        result.setFallbackReason("LLM服务暂不可用，采用规则判断");
        result.setDirectTotalDistanceM(directTotal);
        result.setBatchTotalDistanceM(batchTotal > 0 ? batchTotal : null);

        if (hubRecommended) {
            double savePct = (directTotal - batchTotal) / directTotal * 100;
            result.setReason(String.format(
                    "规则判断：合并配送里程比直送节省约 %.0f%%，超过10%%阈值，建议启用Hub模式", savePct));
            result.setEstimatedSaving(String.format("预计节省约%.0f%%里程", savePct));
            result.setUrgencyLevel("MEDIUM");
            result.setDispersionAnalysis(orderCount > 5 ? "订单较多，分散度高" : "订单数量适中");
        } else {
            result.setReason("规则判断：订单数量较少或分布集中，直送与合并配送里程相差不大，直送更灵活");
            result.setEstimatedSaving("里程节省不显著");
            result.setUrgencyLevel("LOW");
            result.setDispersionAnalysis("订单集中度较高");
        }
        return result;
    }

    // ----------------------------------------------------------------
    //  Haversine 球面距离（米）
    // ----------------------------------------------------------------

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
