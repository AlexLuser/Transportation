package com.fm.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fm.logistics.client.DeepSeekClient;
import com.fm.logistics.dto.HistoryContextDTO;
import com.fm.logistics.dto.HubSelectionResultDTO;
import com.fm.logistics.entity.LogisticsBatch;
import com.fm.logistics.entity.LogisticsHub;
import com.fm.logistics.mapper.LogisticsBatchMapper;
import com.fm.logistics.mapper.LogisticsHubMapper;
import com.fm.logistics.service.LlmHubSelectionService;
import com.fm.logistics.service.RouteHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 智能 Hub 选择服务实现（方案C）
 *
 * 流程：
 *   ① 用 Haversine SQL 查询距订单群中心最近的 3 个候选 Hub
 *   ② 统计每个 Hub 今日已处理的批次数（负载）
 *   ③ 用 RouteHistoryService 查每个 Hub 路段的历史均速（交通状况）
 *   ④ 将三维信息（距离 + 负载 + 交通）组装为 Prompt，调用 DeepSeek
 *   ⑤ 解析 selectedIndex，返回最优 Hub + 理由
 *   ⑥ LLM 失败时降级为纯距离最近（最安全的 fallback）
 */
@Service
public class LlmHubSelectionServiceImpl implements LlmHubSelectionService {

    private static final Logger log = LoggerFactory.getLogger(LlmHubSelectionServiceImpl.class);

    /** 最多送入 LLM 的候选 Hub 数量 */
    private static final int MAX_CANDIDATES = 3;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private LogisticsHubMapper hubMapper;

    @Autowired
    private LogisticsBatchMapper batchMapper;

    @Autowired
    private RouteHistoryService routeHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    // ----------------------------------------------------------------

    @Override
    public HubSelectionResultDTO selectHub(double centerLat, double centerLng,
                                            int orderCount, LocalDateTime plannedTime) {

        // ── ① 查候选 Hub（最近 N 个，状态=0正常） ─────────────────────
        List<LogisticsHub> candidates = hubMapper.selectNearestHubs(
                centerLat, centerLng, MAX_CANDIDATES);

        if (candidates.isEmpty()) {
            log.warn("[HubSelect] 无可用中转站，坐标=({},{})", centerLat, centerLng);
            return HubSelectionResultDTO.noHub("当前区域无可用中转站");
        }
        if (candidates.size() == 1) {
            log.info("[HubSelect] 仅一个候选Hub，直接选择: {}", candidates.get(0).getName());
            return HubSelectionResultDTO.directSelect(candidates.get(0), "区域内仅有一个可用中转站");
        }

        // ── ② 构建每个候选 Hub 的综合信息 ─────────────────────────────
        List<HubSelectionResultDTO.HubCandidateInfo> candidateInfos = new ArrayList<>();
        for (LogisticsHub hub : candidates) {
            HubSelectionResultDTO.HubCandidateInfo info = buildCandidateInfo(
                    hub, centerLat, centerLng, plannedTime);
            candidateInfos.add(info);
        }

        // ── ③ 调用 LLM，失败降级 ──────────────────────────────────────
        try {
            String systemPrompt = "你是物流调度专家，请从候选中转站中选出最优的一个。" +
                    "综合考虑：距离（越近越好）、今日负载（越低越好）、当前交通（越顺畅越好）。" +
                    "必须严格以 JSON 格式输出，不得添加任何说明文字。";

            String userPrompt = buildHubSelectionPrompt(
                    centerLat, centerLng, orderCount, candidateInfos, plannedTime);

            String llmResponse = deepSeekClient.callApi(systemPrompt, userPrompt);

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(llmResponse, Map.class);

            // selectedIndex 从 1 开始，转为数组下标
            int idx = ((Number) parsed.getOrDefault("selectedIndex", 1)).intValue() - 1;
            if (idx < 0 || idx >= candidates.size()) {
                idx = 0;
                log.warn("[HubSelect] LLM返回的selectedIndex越界，降级为第一个候选");
            }

            HubSelectionResultDTO result = new HubSelectionResultDTO();
            result.setSelectedHub(candidates.get(idx));
            result.setReason(String.valueOf(parsed.getOrDefault("reason", "")));
            result.setConfidenceLevel(String.valueOf(parsed.getOrDefault("confidenceLevel", "MEDIUM")));
            result.setCandidates(candidateInfos);
            result.setLlmEnhanced(true);

            log.info("[HubSelect] LLM选择：{}，理由：{}",
                    result.getSelectedHub().getName(), result.getReason());
            return result;

        } catch (Exception e) {
            log.warn("[HubSelect] LLM调用失败，降级为距离最近: {}", e.getMessage());
            HubSelectionResultDTO result = new HubSelectionResultDTO();
            result.setSelectedHub(candidates.get(0));
            result.setReason("LLM服务暂不可用，按距离最近原则选择");
            result.setConfidenceLevel("LOW");
            result.setCandidates(candidateInfos);
            result.setLlmEnhanced(false);
            return result;
        }
    }

    // ----------------------------------------------------------------
    //  构建单个候选 Hub 的综合信息
    // ----------------------------------------------------------------

    private HubSelectionResultDTO.HubCandidateInfo buildCandidateInfo(
            LogisticsHub hub, double centerLat, double centerLng, LocalDateTime plannedTime) {

        HubSelectionResultDTO.HubCandidateInfo info = new HubSelectionResultDTO.HubCandidateInfo();
        info.setHubId(hub.getId());
        info.setHubName(hub.getName());
        info.setHubAddress(hub.getAddress());
        info.setDistanceKm(haversine(centerLat, centerLng,
                hub.getLatitude(), hub.getLongitude()) / 1000.0);
        info.setTodayBatchCount(countTodayBatches(hub.getId()));

        // 从历史轨迹数据获取当前时段该路段的均速
        try {
            HistoryContextDTO ctx = routeHistoryService.buildContext(
                    centerLat, centerLng,
                    hub.getLatitude(), hub.getLongitude(),
                    plannedTime);
            double speed = ctx.getCurrentHourAvgSpeedKmh();
            double ratio = ctx.getSpeedRatio();
            info.setAvgSpeedKmh(speed > 0 ? speed : 30.0);
            if (ratio < 0.6) {
                info.setTrafficStatus("严重拥堵（历史均速" + String.format("%.0f", info.getAvgSpeedKmh()) + "km/h）");
            } else if (ratio < 0.8) {
                info.setTrafficStatus("较拥堵（历史均速" + String.format("%.0f", info.getAvgSpeedKmh()) + "km/h）");
            } else {
                info.setTrafficStatus("交通顺畅（历史均速" + String.format("%.0f", info.getAvgSpeedKmh()) + "km/h）");
            }
        } catch (Exception e) {
            info.setAvgSpeedKmh(30.0);
            info.setTrafficStatus("交通状况未知（无历史数据）");
        }

        return info;
    }

    // ----------------------------------------------------------------
    //  查询今日该 Hub 已处理的批次数
    // ----------------------------------------------------------------

    private int countTodayBatches(Long hubId) {
        try {
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            Long cnt = batchMapper.selectCount(
                    new LambdaQueryWrapper<LogisticsBatch>()
                            .eq(LogisticsBatch::getHubId, hubId)
                            .ge(LogisticsBatch::getCreateTime, startOfToday));
            return cnt == null ? 0 : Math.toIntExact(cnt);
        } catch (Exception e) {
            log.warn("[HubSelect] 查询Hub今日批次数失败，hubId={}: {}", hubId, e.getMessage());
            return 0;
        }
    }

    // ----------------------------------------------------------------
    //  构建 Hub 选择 Prompt
    // ----------------------------------------------------------------

    private String buildHubSelectionPrompt(double centerLat, double centerLng,
                                            int orderCount,
                                            List<HubSelectionResultDTO.HubCandidateInfo> candidates,
                                            LocalDateTime plannedTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 中转站智能选择\n\n");
        sb.append(String.format("**订单群中心**：(%.4f, %.4f)，共 %d 个订单\n",
                centerLat, centerLng, orderCount));
        sb.append(String.format("**计划发车**：%s\n\n",
                plannedTime != null ? plannedTime.toString() : "立即出发"));

        sb.append("**候选中转站列表**（按距离排序）：\n");
        for (int i = 0; i < candidates.size(); i++) {
            HubSelectionResultDTO.HubCandidateInfo c = candidates.get(i);
            sb.append(String.format("%d. **%s**（%s）\n", i + 1, c.getHubName(), c.getHubAddress()));
            sb.append(String.format("   - 距订单群中心：%.2f km\n", c.getDistanceKm()));
            sb.append(String.format("   - 今日已处理批次：%d 批次\n", c.getTodayBatchCount()));
            sb.append(String.format("   - 当前路段交通：%s\n", c.getTrafficStatus()));
        }

        sb.append("\n请综合以上三个维度（距离、负载、交通）给出最优选择，");
        sb.append("**严格**按如下 JSON 格式输出（selectedIndex 从 1 开始计数）：\n");
        sb.append("{\n");
        sb.append("  \"selectedIndex\": 1,\n");
        sb.append("  \"selectedHubName\": \"xxx\",\n");
        sb.append("  \"reason\": \"选择理由（80字以内，说明为什么选这个不选其他的）\",\n");
        sb.append("  \"confidenceLevel\": \"HIGH\"\n");
        sb.append("}");

        return sb.toString();
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
