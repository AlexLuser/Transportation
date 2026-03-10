package com.fm.order.dto;

import com.fm.order.entity.Order;
import com.fm.order.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 订单详情DTO（含订单基本信息和订单项列表）
 */
@Data
@Schema(description = "订单详情")
public class OrderDetailDTO {

    @Schema(description = "订单基本信息")
    private Order order;

    @Schema(description = "订单商品列表")
    private List<OrderItem> items;
}

