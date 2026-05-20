package com.fm.logistics.dto;

import lombok.Data;

import java.util.List;

/**
 * K-Means 聚类单个簇的结果
 */
@Data
public class ClusterResultDTO {

    /** 簇编号（从 0 开始） */
    private int clusterId;

    /** 簇中心纬度 */
    private double centerLat;

    /** 簇中心经度 */
    private double centerLng;

    /** 该簇内的订单列表 */
    private List<DispatchPoolItemDTO> orders;

    /** 簇内订单数 */
    public int getOrderCount() {
        return orders == null ? 0 : orders.size();
    }
}
