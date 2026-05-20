package com.fm.shop.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存扣减消息（#6）接收端 DTO
 * order-service → shop-service
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
