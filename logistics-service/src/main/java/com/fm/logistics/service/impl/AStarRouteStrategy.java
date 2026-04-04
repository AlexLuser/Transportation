package com.fm.logistics.service.impl;

import com.fm.logistics.dto.GeoJsonLineString;
import com.fm.logistics.dto.RouteResultDTO;
import com.fm.logistics.service.RouteStrategy;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 基于 A* 算法的路径规划策略
 *
 * 算法流程：
 *   1. 将起终点经纬度映射到最近的路网节点（通过 LocationIndex）
 *   2. 在路网图上执行 A* 搜索，找到最优路径节点序列
 *   3. 将节点序列转换为 GeoJSON LineString 返回
 *
 * 启发函数：Haversine 球面距离（满足可采纳性，保证算法找到最短路径）
 */
@Service
public class AStarRouteStrategy implements RouteStrategy {

    private static final Logger log = LoggerFactory.getLogger(AStarRouteStrategy.class);

    /** 估算平均行驶速度（km/h），用于计算预计到达时间 */
    private static final double AVG_SPEED_KMH = 40.0;

    @Autowired
    private GraphHopper graphHopper;

    @Override
    public String strategyName() {
        return "A_STAR";
    }

    // ----------------------------------------------------------------
    //  主入口：规划路径
    // ----------------------------------------------------------------

    @Override
    public RouteResultDTO plan(double startLat, double startLon,
                               double endLat,   double endLon) {
        // 标准 A*（epsilon=1.0，保证最优路径）
        return planWithEpsilon(startLat, startLon, endLat, endLon, 1.0);
    }

    /**
     * 带启发函数权重的 A* 规划（Weighted A*）
     * epsilon = 1.0 → 标准最优 A*
     * epsilon > 1.0 → 膨胀启发，更激进地趋向终点，产生不同的备选路线
     *
     * 方案A（LLM_JUDGE）使用此方法生成 3 条不同候选路线：
     *   epsilon=1.0 最优路线、epsilon=1.8 中等激进、epsilon=3.5 高度激进
     */
    public RouteResultDTO planWithEpsilon(double startLat, double startLon,
                                          double endLat,   double endLon,
                                          double epsilon) {
        log.info("[{}] 开始规划(epsilon={}): ({},{}) -> ({},{})",
                strategyName(), epsilon, startLat, startLon, endLat, endLon);
        try {
            BaseGraph     graph         = graphHopper.getBaseGraph();
            NodeAccess    nodeAccess    = graph.getNodeAccess();
            LocationIndex locationIndex = graphHopper.getLocationIndex();

            // ── 第一步：经纬度 → 最近路网节点 ──────────────────────────
            Snap startSnap = locationIndex.findClosest(startLat, startLon, EdgeFilter.ALL_EDGES);
            Snap endSnap   = locationIndex.findClosest(endLat,   endLon,   EdgeFilter.ALL_EDGES);

            if (!startSnap.isValid()) return RouteResultDTO.error("起点不在路网范围内");
            if (!endSnap.isValid())   return RouteResultDTO.error("终点不在路网范围内");

            int startNode = startSnap.getClosestNode();
            int endNode   = endSnap.getClosestNode();
            log.info("[{}] 起点节点={}, 终点节点={}", strategyName(), startNode, endNode);

            if (startNode == endNode) {
                return buildDirectResult(startLat, startLon, endLat, endLon);
            }

            // ── 第二步：带权重的 A* 搜索 ────────────────────────────────
            return runAStar(graph, nodeAccess, startNode, endNode, epsilon);

        } catch (Exception e) {
            log.error("[{}] 规划异常", strategyName(), e);
            return RouteResultDTO.error("路径规划异常: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    //  A* 核心算法
    // ----------------------------------------------------------------

    private RouteResultDTO runAStar(BaseGraph graph, NodeAccess nodeAccess,
                                    int startNode, int endNode, double epsilon) {
        double endLat = nodeAccess.getLat(endNode);
        double endLon = nodeAccess.getLon(endNode);

        // 开放列表：待探索节点，按 f(n) 升序排列，数组格式：[f值, 节点ID]
        PriorityQueue<double[]> openSet = new PriorityQueue<>(
                Comparator.comparingDouble(a -> a[0])
        );

        // g 值表：起点到各节点的已知最短实际距离（米）
        Map<Integer, Double> gScore = new HashMap<>();

        // 路径回溯表：某节点 → 它的上一个节点（用于最终还原完整路径）
        Map<Integer, Integer> cameFrom = new HashMap<>();

        // 已完全探索的节点（不再重复处理）
        Set<Integer> closedSet = new HashSet<>();

        // 初始化起点（h0 乘以 epsilon：epsilon>1 使启发更激进，产生不同的探索路径）
        gScore.put(startNode, 0.0);
        double h0 = epsilon * haversine(nodeAccess.getLat(startNode), nodeAccess.getLon(startNode),
                                        endLat, endLon);
        openSet.add(new double[]{h0, startNode});

        EdgeExplorer explorer = graph.createEdgeExplorer(EdgeFilter.ALL_EDGES);

        while (!openSet.isEmpty()) {
            int current = (int) openSet.poll()[1];

            // ✅ 到达终点
            if (current == endNode) {
                log.info("[{}] 找到路径，开始重建...", strategyName());
                return reconstructPath(cameFrom, nodeAccess, startNode, endNode,
                                       gScore.get(endNode));
            }

            // 已探索过则跳过（优先队列可能存在同一节点的多个旧记录）
            if (closedSet.contains(current)) continue;
            closedSet.add(current);

            // 遍历所有邻接边
            EdgeIterator iter = explorer.setBaseNode(current);
            while (iter.next()) {
                int neighbor = iter.getAdjNode();
                if (closedSet.contains(neighbor)) continue;

                // 经过此边到达邻居的新 g 值
                double newG = gScore.getOrDefault(current, Double.MAX_VALUE)
                              + iter.getDistance();

                // 只有找到更优路径时才更新
                if (newG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    gScore.put(neighbor, newG);
                    cameFrom.put(neighbor, current);

                    double h = epsilon * haversine(nodeAccess.getLat(neighbor), nodeAccess.getLon(neighbor),
                                                   endLat, endLon);
                    openSet.add(new double[]{newG + h, neighbor});
                }
            }
        }

        return RouteResultDTO.error("未找到可行路线（起终点之间无连通路径）");
    }

    // ----------------------------------------------------------------
    //  路径重建：沿 cameFrom 从终点回溯到起点，生成 GeoJSON
    // ----------------------------------------------------------------

    private RouteResultDTO reconstructPath(Map<Integer, Integer> cameFrom,
                                           NodeAccess nodeAccess,
                                           int startNode, int endNode,
                                           double totalDistance) {
        // 从终点往回追溯，得到逆序节点列表，再头插得到正序
        LinkedList<Integer> path = new LinkedList<>();
        int current = endNode;
        while (current != startNode) {
            path.addFirst(current);
            current = cameFrom.get(current);
        }
        path.addFirst(startNode);

        // 转换为 GeoJSON 坐标序列
        GeoJsonLineString routePoints = new GeoJsonLineString();
        for (int nodeId : path) {
            routePoints.addPoint(nodeAccess.getLat(nodeId), nodeAccess.getLon(nodeId));
        }

        // 预计时间（ms）= 距离(m) ÷ 速度(m/s) × 1000
        long durationMs = (long) (totalDistance / (AVG_SPEED_KMH / 3.6) * 1000);

        log.info("[{}] 重建完成：距离={}m，时间={}ms，节点数={}",
                strategyName(), (int) totalDistance, durationMs, path.size());

        return RouteResultDTO.success(totalDistance, durationMs, routePoints);
    }

    // ----------------------------------------------------------------
    //  特殊情况：起终点在同一路网节点，直接返回两点连线
    // ----------------------------------------------------------------

    private RouteResultDTO buildDirectResult(double startLat, double startLon,
                                              double endLat,   double endLon) {
        double dist       = haversine(startLat, startLon, endLat, endLon);
        long   durationMs = (long) (dist / (AVG_SPEED_KMH / 3.6) * 1000);
        GeoJsonLineString lineString = new GeoJsonLineString();
        lineString.addPoint(startLat, startLon).addPoint(endLat, endLon);
        return RouteResultDTO.success(dist, durationMs, lineString);
    }

    // ----------------------------------------------------------------
    //  Haversine 公式：球面两点距离（米）—— A* 启发函数 h(n)
    //  满足可采纳性：球面直线距离 ≤ 实际道路距离，保证 A* 找到最优路径
    // ----------------------------------------------------------------

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0; // 地球平均半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
