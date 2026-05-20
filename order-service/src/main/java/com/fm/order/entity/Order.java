package com.fm.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单信息实体类
 */
@Data
@TableName("order_info")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;         // 订单号（唯一）

    private Long customerId;        // 关联customer_info表的id（顾客）

    private Long shopId;            // 关联shop_info表的id（商户）

    private Long addressId;         // 关联customer_address表的id（收货地址）

    private BigDecimal totalAmount; // 订单总金额（商品金额+运费）

    private BigDecimal productAmount; // 商品总金额

    private BigDecimal shippingFee; // 运费

    private Integer orderStatus;    // 订单状态：0=待支付，1=待发货，2=待揽件，3=派送中，4=已完成，5=已取消

    private Integer paymentStatus;  // 支付状态：0=未支付，1=已支付

    private Date paymentTime;       // 支付时间

    private Date shippingTime;      // 发货时间

    private Date completeTime;      // 完成时间

    private Date cancelTime;        // 取消时间

    private String cancelReason;    // 取消原因

    private String remark;          // 订单备注

    private Long warehouseId;       // 发货仓库ID（商户选择的发货仓库，物流路线起点）

    private Long originHubId;       // 发货城市Hub ID（发货时分配）

    private Long destHubId;         // 收货城市Hub ID（发货时分配）

    private Long flowPlanId;        // MCMF 流量规划单ID（跨城时写入）

    private Long interCityBatchId;  // 跨城干线批次ID（跨城时写入）

    private Integer customerDeleted; // 顾客软删除：0=正常，1=已隐藏

    private Date createTime;        // 创建时间

    private Date updateTime;        // 更新时间
}

