package com.fm.logistics.dto;

import lombok.Data;

/**
 * 请求 ShipmentRoutingController 分配 Hub 的入参
 */
@Data
public class AssignHubsRequestDTO {

    /** 商家选择的发货仓库ID */
    private Long warehouseId;

    /** 收货地址纬度 */
    private Double endLat;

    /** 收货地址经度 */
    private Double endLng;
}
