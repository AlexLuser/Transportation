package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("inventory_check_item")
public class InventoryCheckItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long checkId;

    private Long locationId;

    private Long productId;

    private Long shopId;

    private String productName;

    /** 系统账面数量 */
    private Integer systemQty;

    /** 实盘数量（null=尚未盘点） */
    private Integer actualQty;

    /** PENDING=待盘，COUNTED=已盘，DIFF=有差异 */
    private String status;

    private Date createTime;
    private Date updateTime;
}
