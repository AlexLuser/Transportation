package com.fm.logistics.service.impl;

import com.fm.logistics.dto.McmfResultDTO;
import com.fm.logistics.entity.HubLink;
import com.fm.logistics.entity.NationalHub;
import com.fm.logistics.service.McmfService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCMF 实现：SSP（逐次最短路） + SPFA
 *
 * 建图方式：邻接表残差网络
 *   - 正向边：cap=运量, cost=单位费用
 *   - 反向边：cap=0,    cost=-正向费用（残差）
 * 超级源点 S、汇点 T：每 Hub 拆成 in/out 两节点后接在图末尾。
 */
@Slf4j
@Service
public class McmfServiceImpl implements McmfService {

    // ── 图结构数组（静态分配，避免动态 List 装箱开销）─────────────────
    /** 每物理 Hub 占 2 个节点（入港/出港）+ 超级源汇；需 ≥ 2*|hubs|+2 */
    private static final int MAXN = 2048;
    private static final int MAXE = 8000;  // 最大边数（正反向各一）

    private int[] head = new int[MAXN];
    private int[] nxt  = new int[MAXE];
    private int[] to   = new int[MAXE];
    private int[] cap  = new int[MAXE];
    private long[] cost = new long[MAXE]; // 费用扩大100倍取整，避免浮点误差
    private int edgeCnt;

    private long[] dist   = new long[MAXN];
    private boolean[] inq = new boolean[MAXN];
    private int[] prevv   = new int[MAXN];
    private int[] preve   = new int[MAXN];

    private int S, T;

    @Override
    public McmfResultDTO computeMinCostFlow(
            List<NationalHub> hubs,
            List<HubLink> links,
            Map<Long, Integer> supplyByHub,
            Map<Long, Integer> demandByHub) {

        // ── Step1：每 Hub 两个节点：out(i)=2*i, in(i)=2*i+1，避免「同 Hub 进出」代数和抵消 ──
        Map<Long, Integer> hubIdx = new HashMap<>();
        int idx = 0;
        for (NationalHub h : hubs) {
            hubIdx.put(h.getId(), idx++);
        }
        int hCount = idx;
        if (hCount <= 0) {
            McmfResultDTO empty = new McmfResultDTO();
            empty.setFeasible(true);
            empty.setTotalFlow(0);
            empty.setTotalCost(BigDecimal.ZERO);
            empty.setEdgeFlows(Collections.emptyList());
            return empty;
        }
        S = 2 * hCount;
        T = 2 * hCount + 1;
        int totalNodes = 2 * hCount + 2;
        if (totalNodes > MAXN) {
            throw new IllegalStateException("Hub 数量过大，超过 MCMF 图上限: " + hCount);
        }

        // ── Step2：初始化图 ───────────────────────────────────────────
        Arrays.fill(head, 0, totalNodes, -1);
        edgeCnt = 0;

        int totalSupply = supplyByHub == null ? 0 : supplyByHub.values().stream().mapToInt(Integer::intValue).sum();
        int totalDemand = demandByHub == null ? 0 : demandByHub.values().stream().mapToInt(Integer::intValue).sum();
        if (totalSupply != totalDemand) {
            log.warn("[MCMF] 当日发货单量({})与收货单量({})不一致，仍以收货量为需求上界做流", totalSupply, totalDemand);
        }
        int transshipCap = Math.max(totalSupply, totalDemand);
        if (transshipCap <= 0) {
            transshipCap = 1;
        }

        // Hub 内换载：in → out，允许中转
        for (int i = 0; i < hCount; i++) {
            addEdge(2 * i + 1, 2 * i, transshipCap, 0);
        }

        // 添加实际运输边：出港 → 对方入港
        Map<Integer, HubLink> edgeToLink = new HashMap<>();
        for (HubLink link : links) {
            Integer hi = hubIdx.get(link.getFromHubId());
            Integer hj = hubIdx.get(link.getToHubId());
            if (hi == null || hj == null) continue;
            int uOut = 2 * hi;
            int vIn = 2 * hj + 1;

            long unitCost = link.getCostPerUnit()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            int eid = edgeCnt;
            addEdge(uOut, vIn, link.getCapacityDaily(), unitCost);
            edgeToLink.put(eid, link);
        }

        // 超级源 → 各 Hub 出港（本地发货）
        for (NationalHub h : hubs) {
            Integer i = hubIdx.get(h.getId());
            if (i == null) continue;
            int sup = supplyByHub != null ? supplyByHub.getOrDefault(h.getId(), 0) : 0;
            if (sup > 0) {
                addEdge(S, 2 * i, sup, 0);
            }
        }
        // 各 Hub 入港 → 超级汇（本地收货）
        for (NationalHub h : hubs) {
            Integer i = hubIdx.get(h.getId());
            if (i == null) continue;
            int dem = demandByHub != null ? demandByHub.getOrDefault(h.getId(), 0) : 0;
            if (dem > 0) {
                addEdge(2 * i + 1, T, dem, 0);
            }
        }

        // ── Step3：SSP主循环 ──────────────────────────────────────────
        long minCost = 0;
        int maxFlow = 0;

        while (spfa(totalNodes)) {
            // 沿最短费用路增广
            int flow = Integer.MAX_VALUE;
            int v = T;
            while (v != S) {
                flow = Math.min(flow, cap[preve[v]]);
                v = prevv[v];
            }
            v = T;
            while (v != S) {
                cap[preve[v]] -= flow;
                cap[preve[v] ^ 1] += flow;
                v = prevv[v];
            }
            maxFlow += flow;
            minCost += flow * dist[T];
        }

        // ── Step4：收集各边实际流量 ───────────────────────────────────
        List<McmfResultDTO.EdgeFlow> edgeFlows = new ArrayList<>();
        for (Map.Entry<Integer, HubLink> entry : edgeToLink.entrySet()) {
            int eid = entry.getKey();
            HubLink link = entry.getValue();
            int originalCap = link.getCapacityDaily();
            int remainCap = cap[eid];
            int flow = originalCap - remainCap;
            if (flow > 0) {
                McmfResultDTO.EdgeFlow ef = new McmfResultDTO.EdgeFlow();
                ef.setLinkId(link.getId());
                ef.setFromHubId(link.getFromHubId());
                ef.setToHubId(link.getToHubId());
                ef.setFlowAmount(flow);
                ef.setEdgeCost(link.getCostPerUnit());
                ef.setTotalCost(link.getCostPerUnit()
                        .multiply(BigDecimal.valueOf(flow))
                        .setScale(2, RoundingMode.HALF_UP));
                edgeFlows.add(ef);
            }
        }

        boolean feasible = (maxFlow >= totalDemand);
        if (!feasible) {
            log.warn("[MCMF] 需求无法完全满足: demand={}, actualFlow={}", totalDemand, maxFlow);
        }

        McmfResultDTO result = new McmfResultDTO();
        result.setFeasible(feasible);
        result.setTotalFlow(maxFlow);
        result.setTotalCost(BigDecimal.valueOf(minCost)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        result.setEdgeFlows(edgeFlows);
        return result;
    }

    // ══════════════════════════════════════════════════════════════════
    //  多商品 MCMF：Dijkstra-SSP，每 OD 对独立寻路，共享边容量
    // ══════════════════════════════════════════════════════════════════

    /**
     * 多商品最小费用流。
     *
     * <p>算法：对每个 OD 对（商品）独立执行 Dijkstra-SSP：
     * <ol>
     *   <li>按需求量降序处理 OD 对（高需求优先占容量）。</li>
     *   <li>在以 costPerUnit 为权重、尊重残差容量的有向图上执行 Dijkstra，找最低费用路径。</li>
     *   <li>沿路径增广 min(瓶颈容量, 剩余需求) 单位的流量，更新共享残差容量。</li>
     *   <li>若该 OD 对需求未完全满足（容量耗尽），记录警告，继续处理下一 OD 对。</li>
     * </ol>
     * </p>
     *
     * <p>与单商品 MCMF 的本质区别：每个 OD 对有独立的源/汇，不存在
     * 「同一 Hub 供给与需求代数抵消」的建模缺陷。</p>
     */
    @Override
    public McmfResultDTO computeMultiCommodityFlow(
            List<NationalHub> hubs,
            List<HubLink> links,
            Map<McmfResultDTO.OdPair, Integer> odDemands) {

        // ── 处理边界情况 ─────────────────────────────────────────────
        if (hubs == null || hubs.isEmpty() || odDemands == null || odDemands.isEmpty()) {
            McmfResultDTO empty = new McmfResultDTO();
            empty.setFeasible(true);
            empty.setTotalFlow(0);
            empty.setTotalCost(BigDecimal.ZERO);
            empty.setEdgeFlows(Collections.emptyList());
            empty.setOdRoutes(Collections.emptyList());
            return empty;
        }

        // ── Step1：构建带权有向图 ────────────────────────────────────
        // costAdj : fromHubId → { toHubId → costPerUnit×100（long，避免浮点误差） }
        // residualCap: "fromId-toId" → 剩余容量（跨商品共享）
        Map<Long, Map<Long, Long>> costAdj  = new HashMap<>();
        Map<String, Integer>       resCap   = new HashMap<>();
        Map<String, HubLink>       linkMap  = new HashMap<>();

        for (HubLink link : links) {
            if (link.getCapacityDaily() == null || link.getCapacityDaily() <= 0) continue;
            Long   from = link.getFromHubId();
            Long   to_  = link.getToHubId();
            String key  = edgeKey(from, to_);

            BigDecimal cost = link.getCostPerUnit() != null
                    ? link.getCostPerUnit()
                    : (link.getDistanceKm() != null ? BigDecimal.valueOf(link.getDistanceKm()) : BigDecimal.ONE);
            long costX100 = cost.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

            costAdj.computeIfAbsent(from, k -> new HashMap<>()).put(to_, costX100);
            resCap.put(key, link.getCapacityDaily());
            linkMap.put(key, link);
        }

        // ── Step2：按需求量降序处理每个 OD 对 ───────────────────────
        List<Map.Entry<McmfResultDTO.OdPair, Integer>> sortedOds =
                new ArrayList<>(odDemands.entrySet());
        sortedOds.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Map<String, Integer>                   totalEdgeFlow = new LinkedHashMap<>();
        List<McmfResultDTO.OdRouteResult>      odRoutes      = new ArrayList<>();
        BigDecimal                             totalCost      = BigDecimal.ZERO;
        int                                    totalFlow      = 0;
        int totalDemand = odDemands.values().stream().mapToInt(Integer::intValue).sum();

        for (Map.Entry<McmfResultDTO.OdPair, Integer> entry : sortedOds) {
            McmfResultDTO.OdPair od     = entry.getKey();
            int                  demand = entry.getValue();
            if (demand <= 0) continue;

            List<McmfResultDTO.PathFlow> paths = new ArrayList<>();
            int remaining = demand;

            while (remaining > 0) {
                // Dijkstra：在残差图上找 od.origin → od.dest 的最低费用路径
                DijkstraResult dr = dijkstraOnResidual(costAdj, resCap, od.getOriginHubId(), od.getDestHubId());
                if (dr == null) {
                    log.warn("[MCMF-MC] OD {}→{} 剩余需求 {} 件无可用路径（容量已耗尽）",
                            od.getOriginHubId(), od.getDestHubId(), remaining);
                    break;
                }

                // 瓶颈容量 = 路径上最小残差
                int bottleneck = dr.bottleneck;
                int flow       = Math.min(bottleneck, remaining);

                // 沿路径更新残差容量 & 累计边流量
                for (int i = 0; i < dr.hubPath.size() - 1; i++) {
                    String k = edgeKey(dr.hubPath.get(i), dr.hubPath.get(i + 1));
                    resCap.merge(k, -flow, Integer::sum);
                    totalEdgeFlow.merge(k, flow, Integer::sum);
                }

                // 费用：路径单位费用 × 流量（costX100 → 还原）
                BigDecimal pathCostPerUnit = BigDecimal.valueOf(dr.costX100)
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                totalCost = totalCost.add(pathCostPerUnit.multiply(BigDecimal.valueOf(flow)));
                totalFlow += flow;
                remaining -= flow;

                paths.add(new McmfResultDTO.PathFlow(dr.hubPath, flow));
            }

            if (!paths.isEmpty()) {
                McmfResultDTO.OdRouteResult orr = new McmfResultDTO.OdRouteResult();
                orr.setOriginHubId(od.getOriginHubId());
                orr.setDestHubId(od.getDestHubId());
                orr.setPaths(paths);
                odRoutes.add(orr);
            }
        }

        // ── Step3：汇总各边流量（与前端 MCMF 显示兼容）───────────────
        List<McmfResultDTO.EdgeFlow> edgeFlows = new ArrayList<>();
        for (Map.Entry<String, Integer> ef : totalEdgeFlow.entrySet()) {
            if (ef.getValue() <= 0) continue;
            HubLink link = linkMap.get(ef.getKey());
            if (link == null) continue;
            McmfResultDTO.EdgeFlow flow = new McmfResultDTO.EdgeFlow();
            flow.setLinkId(link.getId());
            flow.setFromHubId(link.getFromHubId());
            flow.setToHubId(link.getToHubId());
            flow.setFlowAmount(ef.getValue());
            flow.setEdgeCost(link.getCostPerUnit());
            flow.setTotalCost((link.getCostPerUnit() != null ? link.getCostPerUnit() : BigDecimal.ONE)
                    .multiply(BigDecimal.valueOf(ef.getValue()))
                    .setScale(2, RoundingMode.HALF_UP));
            edgeFlows.add(flow);
        }

        McmfResultDTO result = new McmfResultDTO();
        result.setFeasible(totalFlow >= totalDemand);
        result.setTotalFlow(totalFlow);
        result.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        result.setEdgeFlows(edgeFlows);
        result.setOdRoutes(odRoutes);
        return result;
    }

    // ── Dijkstra 辅助：在残差图上求 origin→dest 最低费用路径 ─────────

    private static final class DijkstraResult {
        final List<Long> hubPath;   // [origin, ..., dest]
        final long       costX100;  // 路径总费用×100
        final int        bottleneck; // 路径上的最小残差容量

        DijkstraResult(List<Long> hubPath, long costX100, int bottleneck) {
            this.hubPath    = hubPath;
            this.costX100   = costX100;
            this.bottleneck = bottleneck;
        }
    }

    /**
     * Dijkstra（非负权有向图，O((V+E) log V)）。
     * 只走残差容量 > 0 的边。
     * 返回 null 表示 origin→dest 不可达（无正容量路径）。
     */
    private static DijkstraResult dijkstraOnResidual(
            Map<Long, Map<Long, Long>> costAdj,
            Map<String, Integer>       resCap,
            Long origin,
            Long dest) {

        if (origin.equals(dest)) return null;

        Map<Long, Long>  dist   = new HashMap<>();
        Map<Long, Long>  parent = new HashMap<>();
        // PQ：[costX100_accumulated, hubId]
        PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));

        dist.put(origin, 0L);
        pq.offer(new long[]{0L, origin});

        while (!pq.isEmpty()) {
            long[] cur     = pq.poll();
            long   curDist = cur[0];
            Long   u       = cur[1];

            if (curDist > dist.getOrDefault(u, Long.MAX_VALUE)) continue; // 过期条目
            if (u.equals(dest)) break;

            Map<Long, Long> neighbors = costAdj.getOrDefault(u, Collections.emptyMap());
            for (Map.Entry<Long, Long> e : neighbors.entrySet()) {
                Long v    = e.getKey();
                String k  = edgeKey(u, v);
                if (resCap.getOrDefault(k, 0) <= 0) continue; // 无残差容量

                long newDist = curDist + e.getValue();
                if (newDist < dist.getOrDefault(v, Long.MAX_VALUE)) {
                    dist.put(v, newDist);
                    parent.put(v, u);
                    pq.offer(new long[]{newDist, v});
                }
            }
        }

        if (!dist.containsKey(dest)) return null; // dest 不可达

        // 回溯路径
        List<Long> path = new ArrayList<>();
        Long node = dest;
        while (node != null) {
            path.add(0, node);
            node = parent.get(node);
        }
        if (path.isEmpty() || !path.get(0).equals(origin)) return null;

        // 计算瓶颈
        int bottleneck = Integer.MAX_VALUE;
        for (int i = 0; i < path.size() - 1; i++) {
            bottleneck = Math.min(bottleneck,
                    resCap.getOrDefault(edgeKey(path.get(i), path.get(i + 1)), 0));
        }
        if (bottleneck <= 0) return null;

        return new DijkstraResult(path, dist.get(dest), bottleneck);
    }

    private static String edgeKey(Long from, Long to) {
        return from + "-" + to;
    }

    // ── 邻接表加边（正向+反向残差）────────────────────────────────────
    private void addEdge(int u, int v, int c, long w) {
        nxt[edgeCnt] = head[u]; head[u] = edgeCnt;
        to[edgeCnt] = v; cap[edgeCnt] = c; cost[edgeCnt] = w;
        edgeCnt++;

        nxt[edgeCnt] = head[v]; head[v] = edgeCnt;
        to[edgeCnt] = u; cap[edgeCnt] = 0; cost[edgeCnt] = -w;
        edgeCnt++;
    }

    // ── SPFA 最短路（Bellman-Ford 队列优化）──────────────────────────
    private boolean spfa(int n) {
        Arrays.fill(dist, 0, n, Long.MAX_VALUE);
        Arrays.fill(inq, 0, n, false);
        dist[S] = 0;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(S);
        inq[S] = true;

        while (!queue.isEmpty()) {
            int u = queue.poll();
            inq[u] = false;
            for (int e = head[u]; e != -1; e = nxt[e]) {
                int v = to[e];
                if (cap[e] > 0 && dist[u] + cost[e] < dist[v]) {
                    dist[v] = dist[u] + cost[e];
                    prevv[v] = u;
                    preve[v] = e;
                    if (!inq[v]) {
                        inq[v] = true;
                        queue.add(v);
                    }
                }
            }
        }
        return dist[T] != Long.MAX_VALUE;
    }
}
