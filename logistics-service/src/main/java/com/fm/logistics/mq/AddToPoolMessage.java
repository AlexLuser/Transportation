package com.fm.logistics.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接收来自 order-service 的订单入池消息
 */
@Data
@NoArgsConstructor
public class AddToPoolMessage {
    private Long   orderId;
    private Long   shopId;
    private Long   warehouseId;
    private String endAddress;
    private Double endLat;
    private Double endLng;
    private String receiverName;
    private String receiverPhone;
    /** 订单备注（透传至调度池，供急送检测使用） */
    private String remark;

    /** 发货城市 Hub ID */
    private Long originHubId;

    /** 收货城市 Hub ID */
    private Long destHubId;

    /** 是否跨城 */
    private boolean crossCity;
}
