package com.fm.logistics.service;

import com.fm.logistics.dto.ClusterResultDTO;
import com.fm.logistics.dto.DispatchPoolItemDTO;

import java.util.List;

/**
 * 订单聚类服务 — 将调度池中的订单按地理位置聚类，形成批次候选组
 */
public interface ClusterService {

    /**
     * 对订单列表执行 K-Means 地理聚类
     *
     * @param orders 待聚类的订单列表（需含 endLat/endLng）
     * @param k      目标聚类数；若传 null 则自动决策（√n 向上取整）
     * @return 聚类结果列表，每个簇包含订单列表及簇中心坐标
     */
    List<ClusterResultDTO> cluster(List<DispatchPoolItemDTO> orders, Integer k);
}
