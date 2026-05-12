package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("inbound_order_item")
public class InboundOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inboundOrderId;

    private Long productId;

    private String productName;

    private Integer expectedQty;

    private Integer actualQty;

    /** 上架库位ID */
    private Long locationId;

    /** PENDING=待入库，DONE=已入库 */
    private String status;

    private Date createTime;
    private Date updateTime;
}
