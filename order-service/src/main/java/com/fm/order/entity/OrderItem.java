package com.fm.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单项实体类（订单商品明细）
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;           // 关联order_info表的id

    private Long productId;         // 关联product_info表的id

    private String productName;     // 商品名称（下单时的快照）

    private String productImage;    // 商品图片（下单时的快照）

    private BigDecimal productPrice; // 商品单价（下单时的价格）

    private Integer quantity;       // 购买数量

    private BigDecimal subtotal;    // 小计金额（单价*数量）

    private Date createTime;        // 创建时间

    private Date updateTime;        // 更新时间
}

