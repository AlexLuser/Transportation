package com.fm.logistics.dto;

import lombok.Data;

/**
 * 请求 ShipmentRoutingController 分配 Hub 的入参
 * <p>
 * 商户订单：填 warehouseId（从仓库关联的 affiliatedHubId 确定 originHub）
 * 个人寄件：warehouseId=null，填 startLat/startLng（按发件人坐标找最近城市Hub）
 */
@Data
public class AssignHubsRequestDTO {

    /** 商家选择的发货仓库ID（商户订单必填，个人寄件为 null） */
    private Long warehouseId;

    /** 发件人地址纬度（个人寄件时使用，warehouseId 为 null 时必填） */
    private Double startLat;

    /** 发件人地址经度 */
    private Double startLng;

    /** 收货地址纬度 */
    private Double endLat;

    /** 收货地址经度 */
    private Double endLng;
}
