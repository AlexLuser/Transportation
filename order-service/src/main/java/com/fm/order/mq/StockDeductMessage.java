package com.fm.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存扣减消息（#6）
 * order-service → shop-service
 * 场景：创建订单成功后，异步发送库存扣减请求，实现最终一致性
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeductMessage {
    private Long orderId;
    private Long warehouseId;
    private Long productId;
    private Integer quantity;
}
