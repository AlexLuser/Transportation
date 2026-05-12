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

    // ── 个人寄件扩展字段（orderType=1 时有值，商户订单为 null）──

    /**
     * 调度起点类型：0=仓库（商户订单），2=个人取件（上门揽收）
     * 默认 0，null 时消费者同样视为 0
     */
    private Integer dispatchOriginType;

    /** 发件人取件地址文本（司机上门时参考） */
    private String senderAddress;

    /** 发件人纬度 */
    private Double senderLat;

    /** 发件人经度 */
    private Double senderLng;

    /** 发件人姓名（司机联系用） */
    private String senderName;

    /** 发件人电话 */
    private String senderPhone;
}
