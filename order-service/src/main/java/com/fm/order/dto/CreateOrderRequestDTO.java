package com.fm.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求DTO
 */
@Data
@Schema(description = "创建订单请求")
public class CreateOrderRequestDTO {

    @Schema(description = "商户ID", required = true)
    private Long shopId;

    @Schema(description = "收货地址ID", required = true)
    private Long addressId;

    @Schema(description = "订单商品列表", required = true)
    private List<OrderItemDTO> items;

    @Schema(description = "订单备注")
    private String remark;

    /**
     * 订单商品DTO
     */
    @Data
    @Schema(description = "订单商品")
    public static class OrderItemDTO {

        @Schema(description = "商品ID", required = true)
        private Long productId;

        @Schema(description = "购买数量", required = true)
        private Integer quantity;
    }
}

