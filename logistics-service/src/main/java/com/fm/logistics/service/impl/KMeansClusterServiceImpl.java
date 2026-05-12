package com.fm.logistics.service.impl;

import com.fm.logistics.dto.ClusterResultDTO;
import com.fm.logistics.dto.DispatchPoolItemDTO;
import com.fm.logistics.service.ClusterService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 K-Means 算法的地理聚类服务实现
 *
 * 使用 Haversine 距离衡量地理坐标间的球面距离，迭代收敛后输出簇分配结果。
 * 适合将调度池中相邻地区的订单分组，形成批次候选，降低末端配送总里程。
 */
@Service
public class KMeansClusterServiceImpl implements ClusterService {

    private static final int    MAX_ITER     = 100;
    private static final double EARTH_RADIUS = 6371.0; // km

    @Override
    public List<ClusterResultDTO> cluster(List<DispatchPoolItemDTO> orders, Integer k) {
        if (orders == null || orders.isEmpty()) return Collections.emptyList();

        // 过滤出有坐标的订单
        List<DispatchPoolItemDTO> valid = orders.stream()
                .filter(o -> o.getEndLat() != null && o.getEndLng() != null)
                .collect(Collectors.toList());
        if (valid.isEmpty()) return Collections.emptyList();

        int n = valid.size();
        if (k == null || k <= 0) {
            k = Math.max(1, (int) Math.ceil(Math.sqrt(n)));
        }
        k = Math.min(k, n);

        // 初始化：从订单中均匀抽取 k 个作为初始中心
        double[] centerLat = new double[k];
        double[] centerLng = new double[k];
        for (int i = 0; i < k; i++) {
            int idx = (int) Math.round((double) i / (k - 1 == 0 ? 1 : k - 1) * (n - 1));
            centerLat[i] = valid.get(idx).getEndLat();
            centerLng[i] = valid.get(idx).getEndLng();
        }

        int[] assignments = new int[n];
        for (int iter = 0; iter < MAX_ITER; iter++) {
            // E-step: 分配到最近中心
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int best = 0;
                double bestDist = Double.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    double d = haversine(valid.get(i).getEndLat(), valid.get(i).getEndLng(),
                                        centerLat[j], centerLng[j]);
                    if (d < bestDist) { bestDist = d; best = j; }
                }
                if (assignments[i] != best) { assignments[i] = best; changed = true; }
            }
            if (!changed) break;

            // M-step: 重新计算中心（算术平均，在小区域内误差可接受）
            double[] sumLat = new double[k];
            double[] sumLng = new double[k];
            int[]    cnt    = new int[k];
            for (int i = 0; i < n; i++) {
                int c = assignments[i];
                sumLat[c] += valid.get(i).getEndLat();
                sumLng[c] += valid.get(i).getEndLng();
                cnt[c]++;
            }
            for (int j = 0; j < k; j++) {
                if (cnt[j] > 0) {
                    centerLat[j] = sumLat[j] / cnt[j];
                    centerLng[j] = sumLng[j] / cnt[j];
                }
            }
        }

        // 组装结果
        Map<Integer, List<DispatchPoolItemDTO>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(assignments[i], x -> new ArrayList<>()).add(valid.get(i));
        }

        List<ClusterResultDTO> result = new ArrayList<>();
        for (int j = 0; j < k; j++) {
            List<DispatchPoolItemDTO> members = groups.getOrDefault(j, Collections.emptyList());
            if (members.isEmpty()) continue;
            ClusterResultDTO dto = new ClusterResultDTO();
            dto.setClusterId(j);
            dto.setCenterLat(centerLat[j]);
            dto.setCenterLng(centerLng[j]);
            dto.setOrders(members);
            result.add(dto);
        }
        return result;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
