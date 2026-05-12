package com.fm.logistics.service.impl;

import com.fm.logistics.client.DeepSeekClient;
import com.fm.logistics.dto.HistoryContextDTO;
import com.fm.logistics.dto.LlmDecisionResult;
import com.fm.logistics.dto.RouteResultDTO;
import com.fm.logistics.service.RouteHistoryService;
import com.fm.logistics.service.RouteStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 方案A：多候选路线 + LLM 裁判策略
 *
 * 执行流程：
 *   1. 用3种不同 epsilon 值的 Weighted A* 生成3条候选路线（坐标保证来自路网）
 *   2. 从数据库读取历史速度、延误、慢速热点等统计数据
 *   3. 将候选路线 + 历史数据组装成 Prompt，调用 DeepSeek 做裁判
 *   4. 用 LLM 选择的候选路线 + LLM 修正后的预计时间作为最终结果
 *   5. LLM 调用失败时自动降级为 epsilon=1.0 的最优 A* 路线
 */
@Component
public class LlmJudgeRouteStrategy implements RouteStrategy {

    private static final Logger log = LoggerFactory.getLogger(LlmJudgeRouteStrategy.class);

    /** 3种候选路线的启发函数权重：标准最优 / 中等激进 / 高度激进 */
    private static final double[] EPSILONS = {1.0, 1.8, 3.5};

    @Autowired
    private AStarRouteStrategy astarStrategy;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private RouteHistoryService historyService;

    @Override
    public String strategyName() {
        return "LLM_JUDGE";
    }

    @Override
    public RouteResultDTO plan(double startLat, double startLon,
                               double endLat, double endLon,
                               LocalDateTime plannedTime) {
        log.info("[{}] 开始规划: ({},{}) -> ({},{}), plannedTime={}", strategyName(), startLat, startLon, endLat, endLon, plannedTime);

        // ── 第一步：生成多条候选路线 ─────────────────────────────────────
        List<RouteResultDTO> candidates = generateCandidates(startLat, startLon, endLat, endLon);

        if (candidates.isEmpty()) {
            return RouteResultDTO.error("所有候选路线规划均失败");
        }

        // 只有1条有效候选时无需LLM裁判，直接返回
        if (candidates.size() == 1) {
            log.warn("[{}] 仅生成1条有效候选路线，降级返回纯A*结果", strategyName());
            return candidates.get(0);
        }

        // ── 第二步：查询历史数据 ─────────────────────────────────────────
        HistoryContextDTO history = historyService.buildContext(startLat, startLon, endLat, endLon, plannedTime);

        // ── 第三步：调用 LLM 裁判 ────────────────────────────────────────
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt   = buildUserPrompt(candidates, history, startLat, startLon, endLat, endLon);
            String llmResponse  = deepSeekClient.callApi(systemPrompt, userPrompt);
            LlmDecisionResult decision = deepSeekClient.parseDecisionResult(llmResponse);

            // ── 第四步：取 LLM 选中的候选，覆盖预计时间 ──────────────────
            int idx = resolveSelectedIndex(decision, candidates.size());
            RouteResultDTO selected = candidates.get(idx);

            if (decision.getAdjustedDurationMs() != null && decision.getAdjustedDurationMs() > 0) {
                selected.setDurationMs(decision.getAdjustedDurationMs());
            }
            selected.setLlmDecision(decision);
            selected.setLlmEnhanced(true);

            log.info("[{}] LLM选择候选{}（共{}条），调整后时间={}ms，置信度={}",
                    strategyName(), decision.getSelectedCandidate(), candidates.size(),
                    selected.getDurationMs(), decision.getConfidenceLevel());
            return selected;

        } catch (Exception e) {
            log.warn("[{}] LLM调用失败，降级为A*最优路线（epsilon=1.0）: {}", strategyName(), e.getMessage());
            return candidates.get(0);
        }
    }

    // ---- 候选路线生成 ----

    private List<RouteResultDTO> generateCandidates(double startLat, double startLon,
                                                     double endLat, double endLon) {
        List<RouteResultDTO> results = new ArrayList<>();
        for (double eps : EPSILONS) {
            try {
                RouteResultDTO r = astarStrategy.planWithEpsilon(startLat, startLon, endLat, endLon, eps);
                if (r.isSuccess()) {
                    results.add(r);
                }
            } catch (Exception e) {
                log.warn("[{}] epsilon={} 候选规划失败: {}", strategyName(), eps, e.getMessage());
            }
        }
        // 距离差不足1%视为相同路线，去重后只保留距离最短的那条
        return deduplicateCandidates(results);
    }

    private List<RouteResultDTO> deduplicateCandidates(List<RouteResultDTO> candidates) {
        List<RouteResultDTO> unique = new ArrayList<>();
        for (RouteResultDTO c : candidates) {
            boolean isDuplicate = unique.stream().anyMatch(u ->
                    Math.abs(u.getDistanceMeters() - c.getDistanceMeters())
                            / Math.max(u.getDistanceMeters(), 1.0) < 0.01);
            if (!isDuplicate) {
                unique.add(c);
            }
        }
        log.info("[{}] 候选路线生成：共{}条，去重后{}条", strategyName(), candidates.size(), unique.size());
        return unique;
    }

    private int resolveSelectedIndex(LlmDecisionResult decision, int totalSize) {
        if (decision.getSelectedCandidate() == null) return 0;
        int idx = decision.getSelectedCandidate() - 1;
        return Math.max(0, Math.min(idx, totalSize - 1));
    }

    // ---- Prompt 构建 ----

    private String buildSystemPrompt() {
        return "你是一个专业的物流路线优化助手，负责从多条A*候选路线中选出最优方案。\n" +
               "决策优先级：① 准时率（历史延误越少越好）② 路线稳定性（节点数越少越稳定）③ 总距离。\n" +
               "你必须严格以JSON格式输出，不得包含任何JSON以外的文字。";
    }

    private String buildUserPrompt(List<RouteResultDTO> candidates, HistoryContextDTO history,
                                    double startLat, double startLon,
                                    double endLat, double endLon) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 本次规划任务\n");
        sb.append("起点坐标：(").append(startLat).append(", ").append(startLon).append(")\n");
        sb.append("终点坐标：(").append(endLat).append(", ").append(endLon).append(")\n");
        sb.append("计划出发时间：").append(history.getDayOfWeek())
          .append(" ").append(String.format("%02d:00", history.getCurrentHour()))
          .append(" [").append(history.getTimePeriod()).append("]\n\n");

        sb.append("## 候选路线（A*算法规划，坐标已确定，不可修改）\n");
        for (int i = 0; i < candidates.size(); i++) {
            RouteResultDTO c = candidates.get(i);
            int nodeCount = (c.getRoutePoints() != null) ? c.getRoutePoints().size() : 0;
            sb.append("【候选").append(i + 1).append("】")
              .append(" 距离：").append(String.format("%.2f", c.getDistanceMeters() / 1000)).append("km")
              .append("，A*基准时间：").append(c.getDurationMs() / 60000).append("分钟")
              .append("（按40km/h估算）")
              .append("，路网节点数：").append(nodeCount).append("\n");
        }

        sb.append("\n## 历史交通数据（过去30天本系统真实数据）\n");
        sb.append("当前时段(").append(history.getCurrentHour()).append(":00)历史均速：")
          .append(String.format("%.1f", history.getCurrentHourAvgSpeedKmh())).append(" km/h")
          .append("（样本量：").append(history.getSpeedSampleCount()).append("次）\n");
        sb.append("全天历史均速：").append(String.format("%.1f", history.getAllDayAvgSpeedKmh())).append(" km/h\n");
        sb.append("速度比值：").append(String.format("%.2f", history.getSpeedRatio()))
          .append("（<1.0=当前比平时慢，>1.0=比平时快）\n");
        sb.append("当前时段历史平均延误：").append(String.format("%.1f", history.getCurrentHourDelayMin())).append("分钟\n");
        sb.append("近7天起点附近异常路线：").append(history.getExceptionCount()).append("次\n");

        if (!history.getSlowZones().isEmpty()) {
            sb.append("历史慢速热点区域（均速<8km/h）：\n");
            for (HistoryContextDTO.SlowZone zone : history.getSlowZones()) {
                sb.append("  - (").append(zone.getLat()).append(",").append(zone.getLon()).append(")")
                  .append(" 均速").append(String.format("%.1f", zone.getAvgSpeedKmh())).append("km/h")
                  .append("，出现").append(zone.getOccurrences()).append("次\n");
            }
        } else {
            sb.append("历史慢速热点：无记录\n");
        }

        sb.append("\n## 输出格式（严格JSON，不得包含其他内容）\n");
        sb.append("{\n");
        sb.append("  \"selectedCandidate\": <整数，从1到").append(candidates.size()).append("选一个>,\n");
        sb.append("  \"reasoning\": \"<选择理由，引用历史数据说明，不超过60字>\",\n");
        sb.append("  \"adjustedDurationMs\": <用当前时段历史均速重新计算：所选候选距离(m) / (历史均速m/s) * 1000，取整>,\n");
        sb.append("  \"summary\": \"<给用户看的一句话：全程约Xkm，[时段]预计需Y分钟>\",\n");
        sb.append("  \"warnings\": [\"<风险提示1>\"],\n");
        sb.append("  \"confidenceLevel\": \"<HIGH=样本>100 | MEDIUM=10到100 | LOW=<10>\"\n");
        sb.append("}");

        return sb.toString();
    }
}
