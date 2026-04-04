package com.fm.logistics.dto;

import lombok.Data;

/**
 * 创建物流路线请求 DTO
 * 由 order-service 在"订单发货"时调用
 */
@Data
public class CreateRouteRequestDTO {

    /** 订单ID（必填） */
    private Long orderId;

    /** 发货仓库ID（必填） */
    private Long warehouseId;

    /** 出发地址（仓库地址快照，必填） */
    private String startAddress;

    /** 出发地纬度（可选） */
    private Double startLatitude;

    /** 出发地经度（可选） */
    private Double startLongitude;

    /** 收货地址（快照，必填） */
    private String endAddress;

    /** 收货地纬度（可选） */
    private Double endLatitude;

    /** 收货地经度（可选） */
    private Double endLongitude;

    /** 收货人姓名（可选） */
    private String receiverName;

    /** 收货人电话（可选） */
    private String receiverPhone;
}
