package com.fm.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商户备货完成 → 订单进入调度池消息（order-service → logistics-service）
 *
 * 取代原来的 CreateDeliveryMessage，不再立即创建配送单和物流路线，
 * 而是将订单投入调度池，等待管理员/调度系统统一批量调度。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddToPoolMessage {

    /** 订单ID */
    private Long orderId;

    /** 商铺ID */
    private Long shopId;

    /** 发货仓库ID（来自 order.warehouseId） */
    private Long warehouseId;

    /** 收货地址全文 */
    private String endAddress;

    /** 收货地址纬度（可能为 null，若地址未录入坐标） */
    private Double endLat;

    /** 收货地址经度 */
    private Double endLng;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人电话 */
    private String receiverPhone;

    /** 订单备注（用于调度员查看及急送检测） */
    private String remark;

    // ── MCMF 扩展字段 ──────────────────────────────────────────────

    /** 发货城市 Hub ID（ShipmentRoutingService 分配） */
    private Long originHubId;

    /** 收货城市 Hub ID */
    private Long destHubId;

    /** 是否跨城：true=跨城，false=同城 */
    private boolean crossCity;
}
