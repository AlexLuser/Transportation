package com.fm.logistics.dto;

import lombok.Data;
import java.util.List;

/**
 * VRP（Vehicle Routing Problem）规划结果 DTO
 *
 * 使用最近邻启发式算法（Nearest Neighbor Heuristic）求解：
 *   从仓库出发，每次选择 Haversine 距离最近的未访问订单，
 *   O(n²) 复杂度，给出近似最优访问顺序。
 */
@Data
public class VrpResultDTO {

    /** 按访问顺序排列的订单ID列表（第1个最先送） */
    private List<Long> orderedOrderIds;

    /** 按访问顺序排列的坐标列表，每个元素 = [lat, lng] */
    private List<double[]> orderedCoordinates;

    /** 按访问顺序排列的地址快照 */
    private List<String> orderedAddresses;

    /** 按访问顺序排列的收货人姓名 */
    private List<String> orderedReceiverNames;

    /** 按访问顺序排列的收货人电话 */
    private List<String> orderedReceiverPhones;

    /** VRP 规划总距离估算（米，Haversine 直线距离之和） */
    private Double totalDistanceM;

    /** 使用的算法名称 */
    private String algorithm;

    /** 节点总数（含仓库起点） */
    private Integer nodeCount;
}
