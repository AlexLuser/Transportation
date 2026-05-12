package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("transfer_order_item")
public class TransferOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long transferOrderId;

    private Long productId;

    private String productName;

    private Integer quantity;

    private Long srcLocationId;

    private Long dstLocationId;

    /** PENDING=待出库，OUT=已出库，DONE=已入库 */
    private String status;

    private Date createTime;
    private Date updateTime;
}
