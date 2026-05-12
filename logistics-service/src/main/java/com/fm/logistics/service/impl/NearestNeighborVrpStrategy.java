package com.fm.logistics.service.impl;

import com.fm.logistics.dto.VrpResultDTO;
import com.fm.logistics.service.VrpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * VRP 最近邻启发式算法实现（Nearest Neighbor Heuristic）
 *
 * 算法流程：
 *   1. 从仓库（起点）出发
 *   2. 在所有未访问订单中，找 Haversine 球面距离最近的一个
 *   3. 访问该订单，将其标记为已访问
 *   4. 重复步骤 2-3，直到所有订单被访问
 *   5. 返回最优访问顺序
 *
 * 时间复杂度：O(n²)，适合 n ≤ 50 的批次规模
 * 解质量：通常在最优解的 15-25% 以内，满足物流毕设需求
 */
@Service
public class NearestNeighborVrpStrategy implements VrpService {

    private static final Logger log = LoggerFactory.getLogger(NearestNeighborVrpStrategy.class);

    @Override
    public VrpResultDTO computeOptimalOrder(double warehouseLat, double warehouseLng,
                                            List<Long> orderIds,
                                            List<Double> lats, List<Double> lngs,
                                            List<String> addresses,
                                            List<String> receiverNames,
                                            List<String> receiverPhones) {
        int n = orderIds.size();
        log.info("[VRP-NN] 开始规划，订单数={}, 起点=({},{})", n, warehouseLat, warehouseLng);

        boolean[] visited = new boolean[n];
        List<Integer> visitOrder = new ArrayList<>(n);

        double currentLat = warehouseLat;
        double currentLng = warehouseLng;
        double totalDistance = 0;

        // 最近邻贪心：每次从当前位置找最近未访问节点
        for (int step = 0; step < n; step++) {
            int nearestIdx = -1;
            double nearestDist = Double.MAX_VALUE;

            for (int i = 0; i < n; i++) {
                if (!visited[i]) {
                    double dist = haversine(currentLat, currentLng, lats.get(i), lngs.get(i));
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestIdx = i;
                    }
                }
            }

            visited[nearestIdx] = true;
            visitOrder.add(nearestIdx);
            totalDistance += nearestDist;
            currentLat = lats.get(nearestIdx);
            currentLng = lngs.get(nearestIdx);

            log.debug("[VRP-NN] 第{}步：选择订单 orderId={}, 距离={}m",
                    step + 1, orderIds.get(nearestIdx), (int) nearestDist);
        }

        // 按访问顺序构建结果
        VrpResultDTO result = new VrpResultDTO();
        List<Long> orderedIds = new ArrayList<>(n);
        List<double[]> orderedCoords = new ArrayList<>(n);
        List<String> orderedAddresses = new ArrayList<>(n);
        List<String> orderedNames = new ArrayList<>(n);
        List<String> orderedPhones = new ArrayList<>(n);

        for (int idx : visitOrder) {
            orderedIds.add(orderIds.get(idx));
            orderedCoords.add(new double[]{lats.get(idx), lngs.get(idx)});
            orderedAddresses.add(addresses.get(idx));
            orderedNames.add(receiverNames.get(idx));
            orderedPhones.add(receiverPhones.get(idx));
        }

        result.setOrderedOrderIds(orderedIds);
        result.setOrderedCoordinates(orderedCoords);
        result.setOrderedAddresses(orderedAddresses);
        result.setOrderedReceiverNames(orderedNames);
        result.setOrderedReceiverPhones(orderedPhones);
        result.setTotalDistanceM(totalDistance);
        result.setAlgorithm("NEAREST_NEIGHBOR");
        result.setNodeCount(n + 1);

        log.info("[VRP-NN] 规划完成，总距离={}m，访问顺序={}", (int) totalDistance, orderedIds);
        return result;
    }

    // ----------------------------------------------------------------
    //  Haversine 公式：球面两点距离（米）
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
