package com.fm.logistics.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fm.logistics.client.WeatherSnapshotClient;
import com.fm.logistics.dto.*;
import com.fm.logistics.entity.*;
import com.fm.logistics.mapper.*;
import com.fm.logistics.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCMF 流量规划编排服务
 *
 * 流程：
 *  1. 统计各 Hub 当日供给/需求
 *  2. LLM 校准边费用（切入点一）
 *  3. McmfService.computeMinCostFlow（纯算法）
 *  4. 结果落库（flow_plan + flow_plan_item）
 *  5. LLM 解读结果（切入点二）
 *  6. 生成 inter_city_batch
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowPlanServiceImpl implements FlowPlanService {

    @Value("${mcmf.trigger-mode:manual}")
    private String triggerMode;

    @Value("${mcmf.schedule.enabled:false}")
    private boolean scheduleEnabled;

    private final NationalHubMapper nationalHubMapper;
    private final HubLinkMapper hubLinkMapper;
    private final FlowPlanMapper flowPlanMapper;
    private final FlowPlanItemMapper flowPlanItemMapper;
    private final InterCityBatchMapper interCityBatchMapper;
    private final HubSortingRecordMapper hubSortingRecordMapper;

    // 通过 order_info 统计各 Hub 供需（需要 OrderInfoMapper）
    private final OrderInfoMapper orderInfoMapper;

    private final McmfService mcmfService;
    private final LlmEdgeCostCalibrationService llmCalibrationService;
    private final LlmFlowPlanAdviceService llmAdviceService;
    private final ObjectMapper objectMapper;
    private final WeatherSnapshotClient weatherSnapshotClient;

    @Override
    @Transactional
    public FlowPlanDetailDTO triggerManually(LocalDate planDate, LocalDate llmReferenceDate) {
        log.info("[FlowPlan] 开始多商品 MCMF 规划, planDate={}, llmReferenceDate={}", planDate, llmReferenceDate);

        // Step1：查询各 OD 对需求（多商品 MCMF 的商品定义）
        //   每个 (originHub, destHub) 对独立成商品，不做任何 Hub 级聚合/净额，
        //   从根本上消除单商品建模的「供需自消」问题。
        Map<McmfResultDTO.OdPair, Integer> odDemands = buildOdDemands(planDate);
        int totalDemand = odDemands.values().stream().mapToInt(Integer::intValue).sum();

        if (odDemands.isEmpty()) {
            log.warn("[FlowPlan] 今日无跨城订单需要规划");
        } else {
            log.info("[FlowPlan] 共 {} 个 OD 对，合计 {} 件待规划", odDemands.size(), totalDemand);
        }

        // Step2：查全部启用 Hub / Link
        List<NationalHub> hubs  = nationalHubMapper.selectAllActive();
        List<HubLink>     links = hubLinkMapper.selectActiveLinks();
        Map<Long, NationalHub> hubMap = hubs.stream()
                .collect(Collectors.toMap(NationalHub::getId, h -> h));

        // Step2a：LLM 校准（天气/负载仍按服务器真实昨日取数；提示首行「今日」= llmReferenceDate 或 planDate，不向模型说明数据来源）
        CalibrationLlmContext calibCtx = buildCalibrationLlmContext(llmReferenceDate, links, hubMap);
        List<EdgeCostCalibrationDTO>  calibrations = llmCalibrationService.calibrate(links, planDate, calibCtx);
        Map<Long, EdgeCostCalibrationDTO> calibMap = calibrations.stream()
                .collect(Collectors.toMap(EdgeCostCalibrationDTO::getLinkId, c -> c));
        boolean anyLlmEnhanced = calibrations.stream().anyMatch(EdgeCostCalibrationDTO::isLlmEnhanced);

        List<HubLink> calibratedLinks = links.stream().map(l -> {
            HubLink cl = new HubLink();
            cl.setId(l.getId()); cl.setFromHubId(l.getFromHubId()); cl.setToHubId(l.getToHubId());
            cl.setTransportMode(l.getTransportMode()); cl.setCapacityDaily(l.getCapacityDaily());
            cl.setBaseCostPerUnit(l.getBaseCostPerUnit());
            cl.setDistanceKm(l.getDistanceKm()); cl.setDurationHours(l.getDurationHours());
            cl.setIsActive(l.getIsActive());
            EdgeCostCalibrationDTO cal = calibMap.get(l.getId());
            cl.setCostPerUnit(cal != null && cal.isLlmEnhanced()
                    ? l.getBaseCostPerUnit().multiply(BigDecimal.valueOf(cal.getMultiplier()))
                       .setScale(4, RoundingMode.HALF_UP)
                    : l.getBaseCostPerUnit());
            return cl;
        }).collect(Collectors.toList());

        // Step3：多商品 MCMF 求解
        //   算法：Dijkstra-SSP，每个 OD 对在共享残差容量图上独立寻最低费用路径。
        //   结果：edgeFlows（与前端显示兼容）+ odRoutes（含各 OD 对完整路径，用于订单分配）。
        FlowPlan plan = new FlowPlan();
        plan.setPlanDate(planDate);
        plan.setStatus("OPTIMIZING");
        plan.setAlgorithm("MC_MCMF_DIJKSTRA_SSP");
        plan.setLlmEnhanced(anyLlmEnhanced ? 1 : 0);
        plan.setTotalDemand(totalDemand);
        flowPlanMapper.insert(plan);

        McmfResultDTO result;
        try {
            result = mcmfService.computeMultiCommodityFlow(hubs, calibratedLinks, odDemands);
        } catch (Exception e) {
            log.error("[FlowPlan] 多商品 MCMF 计算失败", e);
            plan.setStatus("FAILED");
            flowPlanMapper.updateById(plan);
            throw new RuntimeException("多商品 MCMF 计算失败: " + e.getMessage());
        }

        // Step4：结果落库
        plan.setTotalCost(result.getTotalCost());
        plan.setActualFlow(result.getTotalFlow());
        plan.setFeasible(result.isFeasible() ? 1 : 0);
        plan.setStatus("DONE");
        flowPlanMapper.updateById(plan);

        // 将边流明细写入 flow_plan_item（用于前端 MCMF 图表展示）
        Map<Long, HubLink>     linkMap = calibratedLinks.stream().collect(Collectors.toMap(HubLink::getId, l -> l));
        // 构建 (fromId,toId) → HubLink 快查
        Map<String, HubLink> edgeLinkMap = calibratedLinks.stream()
                .collect(Collectors.toMap(l -> l.getFromHubId() + "-" + l.getToHubId(), l -> l,
                        (a, b) -> a)); // 重复 key 取第一个

        List<FlowPlanItem> planItems = new ArrayList<>();
        for (McmfResultDTO.EdgeFlow ef : result.getEdgeFlows()) {
            FlowPlanItem item = new FlowPlanItem();
            item.setPlanId(plan.getId());
            item.setFromHubId(ef.getFromHubId());
            item.setToHubId(ef.getToHubId());
            item.setLinkId(ef.getLinkId());
            item.setFlowAmount(ef.getFlowAmount());
            item.setEdgeCost(ef.getEdgeCost());
            item.setTotalCost(ef.getTotalCost());
            flowPlanItemMapper.insert(item);
            planItems.add(item);
        }

        // Step4a：LLM 解读结果 + 与费用校准一并持久化到 llm_advice（JSON），供 latest-plan 展示两次调用
        FlowPlanAdviceDTO advice;
        try {
            advice = llmAdviceService.advise(planItems, hubs, calibratedLinks);
        } catch (Exception e) {
            log.warn("[FlowPlan] LLM 规划顾问调用失败: {}", e.getMessage());
            advice = new FlowPlanAdviceDTO();
            advice.setLlmEnhanced(false);
        }
        try {
            persistLlmSnapshot(plan, advice, calibrations);
        } catch (Exception e) {
            log.warn("[FlowPlan] 持久化 LLM 快照失败: {}", e.getMessage());
        }

        // Step5：根据多商品 MCMF 的 OD 路径结果创建 inter_city_batch
        //   所有路径边（含多跳中间段）均建批次，初始状态统一为 "CHAINED"（预规划/待激活）。
        //   后续在 Step6 中，凡有实单绑定第一跳的批次将被激活为 "CREATED"；
        //   后续跳的批次保持 "CHAINED"，中转到达后自动激活，无需重新规划。
        Map<String, InterCityBatch> batchByEdge = new HashMap<>();
        if (result.getOdRoutes() != null) {
            for (McmfResultDTO.OdRouteResult odRoute : result.getOdRoutes()) {
                for (McmfResultDTO.PathFlow pathFlow : odRoute.getPaths()) {
                    List<Long> path = pathFlow.getHubPath();
                    for (int i = 0; i < path.size() - 1; i++) {
                        String edgeKey = path.get(i) + "-" + path.get(i + 1);
                        if (batchByEdge.containsKey(edgeKey)) continue;

                        HubLink link = edgeLinkMap.get(edgeKey);
                        if (link == null) {
                            log.warn("[FlowPlan] 路径边 {} 无对应 hub_link，跳过建批次", edgeKey);
                            continue;
                        }
                        InterCityBatch batch = new InterCityBatch();
                        batch.setBatchNo(generateBatchNo());
                        batch.setFlowPlanId(plan.getId());
                        batch.setFromHubId(path.get(i));
                        batch.setToHubId(path.get(i + 1));
                        batch.setTransportMode(link.getTransportMode());
                        batch.setStatus("CHAINED"); // 统一先建为预规划；有实单的第一跳在 Step6 激活为 CREATED
                        batch.setItemCount(0);
                        interCityBatchMapper.insert(batch);
                        batchByEdge.put(edgeKey, batch);
                    }
                }
            }
        }

        // Step6：根据 OD 路径将实际订单分配到第一跳批次，同时记录每个订单的完整规划路径。
        //   分配后，有实单的批次自动从 CHAINED → CREATED，后续跳批次保持 CHAINED 等中转触发。
        assignOrdersByMultiCommodityResult(plan.getId(), planDate, result.getOdRoutes(), batchByEdge);

        // Step7：清理无实单且非预规划的批次，更新有实单批次的 itemCount；
        //   CHAINED 批次（下游预规划段）保留，中转到达时自动激活。
        removeOrResyncBatchesWithoutOrders(plan.getId());

        log.info("[FlowPlan] 多商品 MCMF 规划完成, planId={}, totalCost={}, OD对数={}, 批次数={}",
                plan.getId(), result.getTotalCost(),
                result.getOdRoutes() != null ? result.getOdRoutes().size() : 0,
                batchByEdge.size());

        return buildDetailDTO(plan, planItems, calibrations, linkMap, hubMap);
    }

    @Override
    public FlowPlanDetailDTO getLatest() {
        FlowPlan plan = flowPlanMapper.selectLatestDone();
        if (plan == null) return null;
        return getById(plan.getId());
    }

    @Override
    public FlowPlanDetailDTO getById(Long id) {
        FlowPlan plan = flowPlanMapper.selectById(id);
        if (plan == null) return null;
        List<FlowPlanItem> items = flowPlanItemMapper.selectByPlanId(id);
        List<NationalHub> hubs = nationalHubMapper.selectAllActive();
        List<HubLink> links = hubLinkMapper.selectActiveLinks();
        Map<Long, HubLink> linkMap = links.stream()
                .collect(Collectors.toMap(HubLink::getId, l -> l));
        Map<Long, NationalHub> hubMap = hubs.stream()
                .collect(Collectors.toMap(NationalHub::getId, h -> h));
        return buildDetailDTO(plan, items, Collections.emptyList(), linkMap, hubMap);
    }

    /** 可选：定时自动触发（application.yml 中 mcmf.schedule.enabled=true 时生效） */
    @Scheduled(cron = "${mcmf.schedule.cron:0 0 6 * * ?}")
    public void scheduledTrigger() {
        if (!scheduleEnabled) return;
        log.info("[FlowPlan] 定时触发多商品 MCMF 规划");
        try {
            triggerManually(LocalDate.now(), null);
        } catch (Exception e) {
            log.error("[FlowPlan] 定时触发失败", e);
        }
    }

    // ── 私有辅助方法 ─────────────────────────────────────────────────

    /**
     * 将数据库中的 OD 对需求查询结果转换为多商品 MCMF 所需的 Map。
     * key = OdPair(originHubId, destHubId)，value = 该 OD 对的待规划订单数。
     */
    private Map<McmfResultDTO.OdPair, Integer> buildOdDemands(LocalDate planDate) {
        List<Map<String, Object>> rows = orderInfoMapper.selectOdPairDemands(planDate);
        Map<McmfResultDTO.OdPair, Integer> odDemands = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long originHubId = longOf(row.get("originHubId"));
            Long destHubId   = longOf(row.get("destHubId"));
            int  cnt         = ((Number) row.get("cnt")).intValue();
            if (originHubId != null && destHubId != null && cnt > 0) {
                odDemands.merge(new McmfResultDTO.OdPair(originHubId, destHubId), cnt, Integer::sum);
            }
        }
        return odDemands;
    }

    /**
     * 根据多商品 MCMF 的 OD 路由结果，将实际订单按比例分配到各自的第一跳批次。
     *
     * <p>分配策略：
     * <ol>
     *   <li>对每个 OD 对，从数据库取出该 OD 对的所有待分配订单（按 ID 升序）。</li>
     *   <li>若该 OD 对有多条路径（因容量分割），按各路径的流量比例依次分配订单。</li>
     *   <li>每个订单写入 planned_path（完整 Hub 路径 JSON），绑定第一跳批次。</li>
     *   <li>有实单绑定的批次从 CHAINED 激活为 CREATED；后续跳批次保持 CHAINED 等中转触发。</li>
     * </ol>
     * </p>
     */
    private void assignOrdersByMultiCommodityResult(
            Long planId,
            LocalDate planDate,
            List<McmfResultDTO.OdRouteResult> odRoutes,
            Map<String, InterCityBatch> batchByEdge) {

        if (odRoutes == null || odRoutes.isEmpty()) {
            log.warn("[FlowPlan] 多商品 MCMF 结果中无 OD 路由，跳过订单分配");
            return;
        }

        List<Map<String, Object>> allOrders = orderInfoMapper.selectUnassignedCrossCityOrders(planDate);
        if (allOrders == null || allOrders.isEmpty()) {
            log.info("[FlowPlan] 无待规划跨城订单");
            return;
        }

        Map<String, List<Long>> ordersByOd = new LinkedHashMap<>();
        for (Map<String, Object> row : allOrders) {
            Long orderId = longOf(row.get("id"));
            Long origin  = longOf(row.get("originHubId"));
            Long dest    = longOf(row.get("destHubId"));
            if (orderId == null || origin == null || dest == null) continue;
            ordersByOd.computeIfAbsent(origin + "-" + dest, k -> new ArrayList<>()).add(orderId);
        }

        int totalAssigned = 0, totalSkipped = 0;
        // 追踪本轮已激活（有实单）的批次，避免重复 updateById
        Set<Long> activatedBatchIds = new HashSet<>();

        for (McmfResultDTO.OdRouteResult odRoute : odRoutes) {
            String odKey = odRoute.getOriginHubId() + "-" + odRoute.getDestHubId();
            List<Long> orderIds = ordersByOd.get(odKey);
            if (orderIds == null || orderIds.isEmpty()) continue;

            List<McmfResultDTO.PathFlow> paths = odRoute.getPaths();
            if (paths == null || paths.isEmpty()) {
                log.warn("[FlowPlan] OD {} 无路径信息，{} 笔订单未分配", odKey, orderIds.size());
                totalSkipped += orderIds.size();
                continue;
            }

            int totalPathFlow = paths.stream().mapToInt(McmfResultDTO.PathFlow::getFlow).sum();
            int orderIdx = 0;

            for (int pi = 0; pi < paths.size(); pi++) {
                McmfResultDTO.PathFlow pathFlow = paths.get(pi);
                if (orderIdx >= orderIds.size()) break;

                Long firstHop = pathFlow.firstHop();
                if (firstHop == null) continue;

                String edgeKey = odRoute.getOriginHubId() + "-" + firstHop;
                InterCityBatch batch = batchByEdge.get(edgeKey);
                if (batch == null) {
                    log.warn("[FlowPlan] OD {} 首跳边 {} 无批次，跳过", odKey, edgeKey);
                    continue;
                }

                int remaining = orderIds.size() - orderIdx;
                int pathOrders = (pi == paths.size() - 1)
                        ? remaining
                        : Math.max(1, (int) Math.round((double) pathFlow.getFlow() / totalPathFlow * orderIds.size()));
                pathOrders = Math.min(pathOrders, remaining);

                List<Long> slice = orderIds.subList(orderIdx, orderIdx + pathOrders);

                // 绑定第一跳批次
                int n = orderInfoMapper.assignOrdersToInterCityBatch(slice, batch.getId(), planId);
                if (n != slice.size()) {
                    log.warn("[FlowPlan] 批次 {} 期望绑定 {} 单，实际写入 {}", batch.getId(), slice.size(), n);
                }
                // 同步将分拣记录标记为已分配（ASSIGNED），与批次绑定，供看板流转至"干线运输"列
                try {
                    hubSortingRecordMapper.batchAssignSorting(slice, batch.getId());
                } catch (Exception e) {
                    log.warn("[FlowPlan] 更新分拣记录失败（不影响批次创建）: batchId={}, err={}", batch.getId(), e.getMessage());
                }

                // 记录完整规划路径（供中转到达后自动衔接下一跳）
                String pathJson = buildPathJson(pathFlow.getHubPath());
                if (!slice.isEmpty()) {
                    orderInfoMapper.updateOrdersPlannedPath(slice, pathJson);
                }

                // 首跳批次有实单，激活为 CREATED
                if (!activatedBatchIds.contains(batch.getId())) {
                    batch.setStatus("CREATED");
                    interCityBatchMapper.updateById(batch);
                    activatedBatchIds.add(batch.getId());
                }

                orderIdx += pathOrders;
                totalAssigned += n;
            }

            if (orderIdx < orderIds.size()) {
                totalSkipped += orderIds.size() - orderIdx;
            }
        }

        log.info("[FlowPlan] 订单分配完成：已分配 {} 笔，跳过 {} 笔，激活批次 {} 个",
                totalAssigned, totalSkipped, activatedBatchIds.size());
    }

    /** 将 Hub ID 列表序列化为 JSON 数组字符串，如 "[1,3,7]" */
    private static String buildPathJson(List<Long> hubPath) {
        if (hubPath == null || hubPath.isEmpty()) return "[]";
        return hubPath.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private void removeOrResyncBatchesWithoutOrders(Long planId) {
        List<InterCityBatch> list = interCityBatchMapper.selectByPlanId(planId);
        for (InterCityBatch b : list) {
            int c = orderInfoMapper.countByInterCityBatchId(b.getId());
            if (c == 0) {
                if ("CHAINED".equals(b.getStatus())) {
                    // 预规划的下游批次暂无实单属正常：中转到达后将自动激活，不能删除
                    log.debug("[FlowPlan] 保留预规划下游批次 {}（边 {}→{}，CHAINED）",
                            b.getId(), b.getFromHubId(), b.getToHubId());
                } else {
                    // CREATED 但无实单：规划分配未覆盖到此边，删除避免无效批次
                    interCityBatchMapper.deleteById(b.getId());
                    log.info("[FlowPlan] 已删除无实单的干线批次 {}（边 {}→{}）",
                            b.getId(), b.getFromHubId(), b.getToHubId());
                }
            } else {
                b.setItemCount(c);
                interCityBatchMapper.updateById(b);
            }
        }
    }

    private static Long longOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.parseLong(o.toString());
    }

    private String generateBatchNo() {
        return "IB" + System.currentTimeMillis() + String.format("%04d", (int)(Math.random() * 10000));
    }

    /**
     * 费用校准 LLM 上下文：边流量与天气按服务器真实「昨日」从库/API 取数；
     * 拼进模型的字符串不写 plan_date、flow_plan_id、归档日期等；{@code llmReferenceDate} 用于提示首行「今日」（可模拟）。
     */
    private CalibrationLlmContext buildCalibrationLlmContext(
            LocalDate llmReferenceDate,
            List<HubLink> links,
            Map<Long, NationalHub> hubMap) {
        LocalDate serverYesterday = LocalDate.now().minusDays(1);
        FlowPlan yPlan = flowPlanMapper.selectLatestDoneOnDate(serverYesterday);
        List<FlowPlanItem> yItems = (yPlan != null)
                ? flowPlanItemMapper.selectByPlanId(yPlan.getId()) : Collections.emptyList();
        Map<Long, HubLink> linkById = links.stream()
                .collect(Collectors.toMap(HubLink::getId, l -> l, (a, b) -> a));
        StringBuilder ySb = new StringBuilder();
        if (yItems.isEmpty()) {
            ySb.append("暂无各边流量与容量数据。");
        } else {
            ySb.append("各边流量与容量：\n");
            for (FlowPlanItem it : yItems) {
                HubLink hl = linkById.get(it.getLinkId());
                int cap = hl != null ? hl.getCapacityDaily() : 0;
                double rate = cap > 0 ? (100.0 * it.getFlowAmount() / cap) : -1;
                String from = hubCalibLabel(hubMap, it.getFromHubId());
                String to = hubCalibLabel(hubMap, it.getToHubId());
                if (rate >= 0) {
                    ySb.append(String.format("  linkId=%d %s→%s 流量=%d 日容=%d 负载约%.0f%%\n",
                            it.getLinkId(), from, to, it.getFlowAmount(), cap, rate));
                } else {
                    ySb.append(String.format("  linkId=%d %s→%s 流量=%d\n",
                            it.getLinkId(), from, to, it.getFlowAmount()));
                }
            }
        }
        String weather = weatherSnapshotClient.summarizeForDate(serverYesterday);
        return new CalibrationLlmContext(llmReferenceDate, ySb.toString().trim(), weather);
    }

    private static String hubCalibLabel(Map<Long, NationalHub> hubMap, long id) {
        NationalHub h = hubMap.get(id);
        return h != null ? h.getName() : ("Hub" + id);
    }

    private FlowPlanDetailDTO buildDetailDTO(FlowPlan plan, List<FlowPlanItem> items,
                                              List<EdgeCostCalibrationDTO> calibrations,
                                              Map<Long, HubLink> linkMap,
                                              Map<Long, NationalHub> hubMap) {
        FlowPlanDetailDTO dto = new FlowPlanDetailDTO();
        dto.setPlan(plan);
        dto.setCalibrations(calibrations);

        List<FlowPlanDetailDTO.FlowPlanItemVO> vos = items.stream().map(item -> {
            FlowPlanDetailDTO.FlowPlanItemVO vo = new FlowPlanDetailDTO.FlowPlanItemVO();
            vo.setLinkId(item.getLinkId());
            vo.setFromHubId(item.getFromHubId());
            vo.setToHubId(item.getToHubId());
            HubLink link = linkMap.get(item.getLinkId());
            NationalHub from = hubMap.get(item.getFromHubId());
            NationalHub to   = hubMap.get(item.getToHubId());
            vo.setFromHubName(from != null ? from.getName() : "?");
            vo.setToHubName(to != null ? to.getName() : "?");
            vo.setTransportMode(link != null ? link.getTransportMode() : "");
            vo.setFlowAmount(item.getFlowAmount());
            vo.setCapacityDaily(link != null ? link.getCapacityDaily() : 0);
            vo.setLoadRate(link != null && link.getCapacityDaily() > 0
                    ? (double) item.getFlowAmount() / link.getCapacityDaily() : 0);
            vo.setEdgeCost(item.getEdgeCost());
            vo.setBaseCost(link != null ? link.getBaseCostPerUnit() : item.getEdgeCost());
            vo.setTotalCost(item.getTotalCost());
            return vo;
        }).collect(Collectors.toList());

        dto.setItems(vos);
        attachLlmFromPlan(plan, dto, linkMap, hubMap);
        return dto;
    }

    private static final TypeReference<List<EdgeCostCalibrationDTO>> CALIB_LIST_TYPE =
            new TypeReference<>() {};

    /** 将两次 LLM 结果写入 flow_plan.llm_advice（JSON） */
    private void persistLlmSnapshot(FlowPlan plan, FlowPlanAdviceDTO advice,
                                     List<EdgeCostCalibrationDTO> calibrations) throws Exception {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("v", 1);
        snap.put("advice", advice);
        snap.put("calibrations", calibrations != null ? calibrations : Collections.emptyList());
        plan.setLlmAdvice(objectMapper.writeValueAsString(snap));
        boolean anyCalib = calibrations != null
                && calibrations.stream().anyMatch(EdgeCostCalibrationDTO::isLlmEnhanced);
        plan.setLlmEnhanced(anyCalib || advice.isLlmEnhanced() ? 1 : 0);
        flowPlanMapper.updateById(plan);
    }

    /**
     * 从 llm_advice 解析两次 LLM 结果；兼容旧数据（纯文本摘要）。
     * 响应中的 plan.llmAdvice 改写为简短摘要，避免把整段 JSON 暴露给旧字段。
     */
    private void attachLlmFromPlan(FlowPlan plan, FlowPlanDetailDTO dto,
                                   Map<Long, HubLink> linkMap,
                                   Map<Long, NationalHub> hubMap) {
        String raw = plan.getLlmAdvice();
        if (raw == null || raw.isBlank()) {
            return;
        }
        FlowPlanAdviceDTO advice = null;
        List<EdgeCostCalibrationDTO> cals = null;
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.has("advice") || root.has("calibrations")) {
                if (root.has("advice")) {
                    advice = objectMapper.convertValue(root.get("advice"), FlowPlanAdviceDTO.class);
                }
                if (root.has("calibrations") && root.get("calibrations").isArray()) {
                    cals = objectMapper.convertValue(root.get("calibrations"), CALIB_LIST_TYPE);
                }
            } else if (root.has("bottleneckAnalysis") || root.has("summary")) {
                advice = objectMapper.convertValue(root, FlowPlanAdviceDTO.class);
            }
        } catch (Exception e) {
            advice = new FlowPlanAdviceDTO();
            advice.setSummary(raw.trim());
            advice.setLlmEnhanced(plan.getLlmEnhanced() != null && plan.getLlmEnhanced() == 1);
        }
        if (advice != null) {
            dto.setLlmFlowAdvice(advice);
            String summary = advice.getSummary();
            if (summary != null && !summary.isBlank()) {
                plan.setLlmAdvice(summary);
            } else if (advice.getBottleneckAnalysis() != null && !advice.getBottleneckAnalysis().isBlank()) {
                plan.setLlmAdvice(advice.getBottleneckAnalysis());
            } else {
                plan.setLlmAdvice(null);
            }
        }
        if (cals != null && !cals.isEmpty()) {
            dto.setCalibrations(cals);
        }
        enrichCalibrationRoutes(dto.getCalibrations(), linkMap, hubMap);
    }

    private void enrichCalibrationRoutes(List<EdgeCostCalibrationDTO> calibrations,
                                         Map<Long, HubLink> linkMap,
                                         Map<Long, NationalHub> hubMap) {
        if (calibrations == null) {
            return;
        }
        for (EdgeCostCalibrationDTO c : calibrations) {
            HubLink link = linkMap.get(c.getLinkId());
            if (link == null) {
                continue;
            }
            NationalHub from = hubMap.get(link.getFromHubId());
            NationalHub to = hubMap.get(link.getToHubId());
            c.setRouteLabel((from != null ? from.getName() : "?") + " → "
                    + (to != null ? to.getName() : "?"));
        }
    }
}
