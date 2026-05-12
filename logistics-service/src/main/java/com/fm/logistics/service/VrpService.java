package com.fm.logistics.service;

import com.fm.logistics.dto.VrpResultDTO;

import java.util.List;

/**
 * VRP（Vehicle Routing Problem）求解接口
 *
 * 当前实现：最近邻启发式（Nearest Neighbor Heuristic）
 *   - 时间复杂度：O(n²)，适合 n ≤ 50 的批次规模
 *   - 保证找到可行解，近似最优（通常在最优解的 20% 以内）
 */
public interface VrpService {

    /**
     * 计算多个订单目的地的最优（近似）访问顺序
     *
     * @param warehouseLat  仓库纬度（起点）
     * @param warehouseLng  仓库经度（起点）
     * @param orderIds      订单ID列表
     * @param lats          订单目的地纬度列表（与orderIds一一对应）
     * @param lngs          订单目的地经度列表（与orderIds一一对应）
     * @param addresses     订单目的地地址列表（与orderIds一一对应）
     * @param receiverNames 收货人姓名列表
     * @param receiverPhones 收货人电话列表
     * @return VRP 规划结果，含最优访问顺序和总距离
     */
    VrpResultDTO computeOptimalOrder(double warehouseLat, double warehouseLng,
                                     List<Long> orderIds,
                                     List<Double> lats, List<Double> lngs,
                                     List<String> addresses,
                                     List<String> receiverNames,
                                     List<String> receiverPhones);
}
