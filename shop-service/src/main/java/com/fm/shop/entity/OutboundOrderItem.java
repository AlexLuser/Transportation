package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("outbound_order_item")
public class OutboundOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long outboundOrderId;

    private Long productId;

    private String productName;

    private Integer quantity;

    /** 拣货库位ID */
    private Long locationId;

    /** PENDING=待拣货，DONE=已出库 */
    private String status;

    private Date createTime;
    private Date updateTime;
}
