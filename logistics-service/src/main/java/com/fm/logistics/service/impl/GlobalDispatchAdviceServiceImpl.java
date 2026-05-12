package com.fm.logistics.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fm.logistics.client.DeepSeekClient;
import com.fm.logistics.dto.ClusterResultDTO;
import com.fm.logistics.dto.DispatchPoolItemDTO;
import com.fm.logistics.dto.GlobalDispatchAdviceDTO;
import com.fm.logistics.entity.LogisticsHub;
import com.fm.logistics.mapper.LogisticsHubMapper;
import com.fm.logistics.service.GlobalDispatchAdviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 全局调度顾问实现
 *
 * 对 K-Means 聚类结果调用 DeepSeek，让 AI 从宏观视角给出：
 *  - 每个簇是否走 Hub 模式
 *  - 建议使用哪个 Hub
 *  - 紧急程度与特殊说明
 * 包含规则降级，LLM 失败时自动回退。
 */
@Slf4j
@Service
public class GlobalDispatchAdviceServiceImpl implements GlobalDispatchAdviceService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    /** 簇内订单数超过此阈值时建议走 Hub */
    private static final int    HUB_THRESHOLD   = 3;
    /** 距仓库超过此距离（km）时建议走 Hub */
    private static final double HUB_DIST_KM     = 10.0;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private LogisticsHubMapper logisticsHubMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public GlobalDispatchAdviceDTO advise(List<ClusterResultDTO> clusters, Long warehouseId,
                                          Double warehouseLat, Double warehouseLng) {
        if (clusters == null || clusters.isEmpty()) {
            GlobalDispatchAdviceDTO empty = new GlobalDispatchAdviceDTO();
            empty.setStrategy("NO_ORDERS");
            empty.setReason("调度池为空，无需调度");
            empty.setBatchSuggestions(new ArrayList<>());
            return empty;
        }

        // 每个簇找最近 Hub，并计算仓库→簇距离
        List<HubInfo> hubInfos = new ArrayList<>();
        for (ClusterResultDTO c : clusters) {
            List<LogisticsHub> hubs = logisticsHubMapper.selectNearestHubs(
                    c.getCenterLat(), c.getCenterLng(), 1);
            double hubDistKm = hubs.isEmpty() ? Double.MAX_VALUE
                    : haversine(c.getCenterLat(), c.getCenterLng(),
                                hubs.get(0).getLatitude(), hubs.get(0).getLongitude());
            // 仓库→簇距离（未知时置 -1）
            double warehouseDistKm = (warehouseLat != null && warehouseLng != null)
                    ? haversine(warehouseLat, warehouseLng, c.getCenterLat(), c.getCenterLng())
                    : -1;
            hubInfos.add(hubs.isEmpty() ? null
                    : new HubInfo(hubs.get(0), hubDistKm, warehouseDistKm));
        }

        try {
            String json = deepSeekClient.callApi(
                    buildSystemPrompt(),
                    buildUserPrompt(clusters, hubInfos, warehouseLat, warehouseLng));
            return parseResponse(json, clusters, hubInfos, true);
        } catch (Exception e) {
            log.warn("[全局调度顾问] LLM 调用失败，使用规则降级: {}", e.getMessage());
            return fallback(clusters, hubInfos);
        }
    }

    // ----------------------------------------------------------------

    private String buildSystemPrompt() {
        return """
                你是一名资深物流调度专家，熟悉"仓库→中转站→客户"的集散配送模式（Hub-and-Spoke）。
                
                核心决策原则：
                1. 【走中转站（useHub=true）】当订单目的地集群距离发货仓库较远（>8km），
                   且附近存在合适的中转站时，应走中转模式：
                   仓库统一发一辆干线车到中转站（节省多次往返），
                   再由中转站安排末端配送员分别送达各客户。
                   订单数越多、距仓库越远，中转收益越显著。
                2. 【直接送达（useHub=false）】当订单数量极少（1~2单）或目的地距仓库很近（<5km），
                   中转带来的额外路程反而增加成本，应直接从仓库送达。
                3. 【中转站位置判断】中转站到目的地集群的距离越近，说明中转站就在客户区，
                   适合作为末端配送基地；若中转站距客户集群也很远（>15km），则中转意义不大。
                
                请严格按 JSON 格式返回，不要解释。
                """;
    }

    private String buildUserPrompt(List<ClusterResultDTO> clusters, List<HubInfo> hubInfos,
                                   Double warehouseLat, Double warehouseLng) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 物流调度分析\n\n");

        if (warehouseLat != null && warehouseLng != null) {
            sb.append(String.format("**发货仓库位置**：(%.4f, %.4f)\n\n", warehouseLat, warehouseLng));
        } else {
            sb.append("**发货仓库位置**：未知\n\n");
        }

        sb.append(String.format("**总待调度订单**：%d 单，**批次数**：%d\n\n",
                clusters.stream().mapToInt(ClusterResultDTO::getOrderCount).sum(), clusters.size()));

        for (int i = 0; i < clusters.size(); i++) {
            ClusterResultDTO c = clusters.get(i);
            HubInfo hi = hubInfos.get(i);
            sb.append(String.format("### 批次 %d（clusterId=%d）\n", i + 1, c.getClusterId()));
            sb.append(String.format("- 订单数：%d\n", c.getOrderCount()));
            sb.append(String.format("- 目的地集群中心：(%.4f, %.4f)\n", c.getCenterLat(), c.getCenterLng()));

            // 仓库→簇距离（决定是否需要干线集散）
            if (hi != null && hi.warehouseDistKm >= 0) {
                sb.append(String.format("- **仓库→目的地集群距离：%.2f km**（%s）\n",
                        hi.warehouseDistKm,
                        hi.warehouseDistKm < 5  ? "较近，直送成本低" :
                        hi.warehouseDistKm < 10 ? "中等距离" : "较远，干线集散有收益"));
            }

            // Hub→簇距离（决定 Hub 末端覆盖能力）
            if (hi != null) {
                sb.append(String.format("- 最近中转站：%s（%s）\n", hi.hub.getName(), hi.hub.getAddress()));
                sb.append(String.format("  - 中转站→目的地集群距离：%.2f km（%s）\n",
                        hi.distKm,
                        hi.distKm < 5  ? "中转站就在客户区，末端配送方便" :
                        hi.distKm < 15 ? "中转站离客户区尚可" : "中转站距客户较远，末端效益有限"));
            } else {
                sb.append("- 最近中转站：无可用中转站，只能直送\n");
            }

            // 目的地摘要
            sb.append("- 目的地摘要：");
            int show = Math.min(3, c.getOrders().size());
            for (int j = 0; j < show; j++) {
                sb.append(c.getOrders().get(j).getEndAddress()).append("；");
            }
            if (c.getOrders().size() > show) sb.append("等");
            sb.append("\n\n");
        }

        sb.append("""
                请以如下 JSON 格式返回（不要有多余字段）：
                {
                  "suggestedBatchCount": <int>,
                  "strategy": "<整体策略一句话描述>",
                  "reason": "<综合分析，说明各批次决策依据>",
                  "estimatedSavingKm": <float>,
                  "batchSuggestions": [
                    {
                      "clusterId": <int>,
                      "useHub": <true|false>,
                      "suggestedHubName": "<推荐中转站名称，不走中转填null>",
                      "urgency": "<LOW|MEDIUM|HIGH>",
                      "note": "<本批次决策说明，重点说明距离和订单数的判断依据>"
                    }
                  ]
                }
                """);
        return sb.toString();
    }

    private GlobalDispatchAdviceDTO parseResponse(String json, List<ClusterResultDTO> clusters,
                                                  List<HubInfo> hubInfos, boolean llmEnhanced) {
        try {
            JsonNode root = objectMapper.readTree(json);
            GlobalDispatchAdviceDTO dto = new GlobalDispatchAdviceDTO();
            dto.setSuggestedBatchCount(root.path("suggestedBatchCount").asInt(clusters.size()));
            dto.setStrategy(root.path("strategy").asText("HUB_AND_SPOKE"));
            dto.setReason(root.path("reason").asText(""));
            dto.setEstimatedSavingKm(root.path("estimatedSavingKm").asDouble(0));
            dto.setLlmEnhanced(llmEnhanced);

            List<GlobalDispatchAdviceDTO.BatchSuggestion> suggestions = new ArrayList<>();
            JsonNode arr = root.path("batchSuggestions");
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    GlobalDispatchAdviceDTO.BatchSuggestion bs = new GlobalDispatchAdviceDTO.BatchSuggestion();
                    bs.setClusterId(node.path("clusterId").asInt());
                    bs.setUseHub(node.path("useHub").asBoolean(false));
                    bs.setSuggestedHubName(node.path("suggestedHubName").asText(null));
                    bs.setUrgency(node.path("urgency").asText("MEDIUM"));
                    bs.setNote(node.path("note").asText(""));
                    // 根据 hubName 反查 hubId
                    int idx = findClusterIdx(clusters, bs.getClusterId());
                    if (bs.isUseHub() && idx >= 0 && hubInfos.get(idx) != null) {
                        bs.setSuggestedHubId(hubInfos.get(idx).hub.getId());
                        if (bs.getSuggestedHubName() == null || bs.getSuggestedHubName().isBlank()) {
                            bs.setSuggestedHubName(hubInfos.get(idx).hub.getName());
                        }
                    }
                    suggestions.add(bs);
                }
            }
            dto.setBatchSuggestions(suggestions);
            return dto;
        } catch (Exception e) {
            log.warn("[全局调度顾问] 解析 LLM 响应失败，降级: {}", e.getMessage());
            return fallback(clusters, hubInfos);
        }
    }

    private GlobalDispatchAdviceDTO fallback(List<ClusterResultDTO> clusters, List<HubInfo> hubInfos) {
        GlobalDispatchAdviceDTO dto = new GlobalDispatchAdviceDTO();
        dto.setSuggestedBatchCount(clusters.size());
        dto.setStrategy("RULE_BASED_FALLBACK");
        dto.setReason("LLM 不可用，已按规则自动分析：仓库→目的地较远且订单量充足时走中转模式");
        dto.setLlmEnhanced(false);

        List<GlobalDispatchAdviceDTO.BatchSuggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            ClusterResultDTO c = clusters.get(i);
            HubInfo hi = hubInfos.get(i);
            GlobalDispatchAdviceDTO.BatchSuggestion bs = new GlobalDispatchAdviceDTO.BatchSuggestion();
            bs.setClusterId(c.getClusterId());

            // 规则：① 仓库→簇距离已知且 > HUB_DIST_KM，且订单数 >= 阈值 → 走 Hub
            //        ② 仓库距离未知时降级：订单数多且存在近 Hub → 走 Hub
            boolean farFromWarehouse = hi != null && hi.warehouseDistKm > HUB_DIST_KM;
            boolean unknownWarehouseDist = hi == null || hi.warehouseDistKm < 0;
            boolean hubNearCluster = hi != null && hi.distKm < 10.0;
            boolean enoughOrders = c.getOrderCount() >= HUB_THRESHOLD;

            boolean useHub = hi != null && (
                    (farFromWarehouse && enoughOrders)          // 仓库远且单量足够 → 中转合算
                    || (unknownWarehouseDist && enoughOrders && hubNearCluster)  // 不知仓库位置时保守判断
            );
            bs.setUseHub(useHub);
            if (useHub) {
                bs.setSuggestedHubId(hi.hub.getId());
                bs.setSuggestedHubName(hi.hub.getName());
            }
            bs.setUrgency(c.getOrderCount() >= 5 ? "HIGH" : "MEDIUM");
            String reason = useHub
                    ? String.format("仓库→目的地约 %.1f km，%d 单集中配送，走中转更经济",
                            hi != null ? hi.warehouseDistKm : 0, c.getOrderCount())
                    : (c.getOrderCount() < HUB_THRESHOLD
                            ? "订单数少（" + c.getOrderCount() + "单），直送更灵活"
                            : "目的地距仓库较近，直送成本低");
            bs.setNote(reason);
            suggestions.add(bs);
        }
        dto.setBatchSuggestions(suggestions);
        return dto;
    }

    // ----------------------------------------------------------------
    //  紧急性检测（独立 LLM 调用，调度池加载时使用）
    // ----------------------------------------------------------------

    @Override
    public List<Long> checkUrgency(List<DispatchPoolItemDTO> items) {
        // 只传有备注的订单给 LLM
        List<DispatchPoolItemDTO> withRemark = items.stream()
                .filter(o -> o.getRemark() != null && !o.getRemark().isBlank())
                .collect(java.util.stream.Collectors.toList());
        if (withRemark.isEmpty()) return new ArrayList<>();

        String systemPrompt = """
                你是一名物流紧急判断助手。
                对给出的订单备注，判断哪些包含"需要加急配送"的意思
                （如：急送、加急、紧急、今天必须到、急用、需要马上送、赶紧、尽快等语义）。
                只返回 JSON，不要解释：{"urgentOrderIds": [orderId, ...]}
                无紧急单则返回 {"urgentOrderIds": []}
                """;

        StringBuilder sb = new StringBuilder("以下订单需要判断是否紧急：\n");
        for (DispatchPoolItemDTO o : withRemark) {
            sb.append(String.format("- orderId=%d，备注：「%s」\n", o.getOrderId(), o.getRemark()));
        }

        try {
            String json = deepSeekClient.callApi(systemPrompt, sb.toString());
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("urgentOrderIds");
            List<Long> result = new ArrayList<>();
            if (arr.isArray()) arr.forEach(n -> result.add(n.asLong()));
            return result;
        } catch (Exception e) {
            log.warn("[紧急检测] LLM 调用失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private int findClusterIdx(List<ClusterResultDTO> clusters, int clusterId) {
        for (int i = 0; i < clusters.size(); i++) {
            if (clusters.get(i).getClusterId() == clusterId) return i;
        }
        return -1;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static class HubInfo {
        final LogisticsHub hub;
        /** 簇中心→Hub 距离（km） */
        final double distKm;
        /** 仓库→簇中心距离（km），-1 表示仓库坐标未知 */
        final double warehouseDistKm;
        HubInfo(LogisticsHub hub, double distKm, double warehouseDistKm) {
            this.hub = hub;
            this.distKm = distKm;
            this.warehouseDistKm = warehouseDistKm;
        }
    }
}
