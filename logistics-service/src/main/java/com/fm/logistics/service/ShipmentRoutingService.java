package com.fm.logistics.service;

import com.fm.logistics.dto.HubAssignmentDTO;

public interface ShipmentRoutingService {

    /**
     * 根据发货起点和收货坐标，分配 originHub / destHub，判断是否跨城。
     * <ul>
     *   <li>商户订单：warehouseId 不为 null，通过仓库 affiliatedHubId 定起点 Hub</li>
     *   <li>个人寄件：warehouseId 为 null，通过 startLat/startLng 找最近城市 Hub</li>
     * </ul>
     */
    HubAssignmentDTO assignHubs(Long warehouseId,
                                Double startLat, Double startLng,
                                Double endLat, Double endLng);
}
