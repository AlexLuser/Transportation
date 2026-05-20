package com.fm.logistics.service;

import com.fm.logistics.dto.HubAssignmentDTO;

public interface ShipmentRoutingService {

    /**
     * 根据发货仓库和收货坐标，分配 originHub / destHub，判断是否跨城
     */
    HubAssignmentDTO assignHubs(Long warehouseId, Double endLat, Double endLng);
}
