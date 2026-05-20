package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 入库单
 * 记录货物进入商家发货仓的完整流程
 */
@Data
@TableName("inbound_order")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单号（IB+时间戳+4位随机） */
    private String orderNo;

    private Long warehouseId;

    private Long shopId;

    /** 来源类型：PURCHASE=采购，TRANSFER=调拨，RETURN=退货 */
    private String sourceType;

    /** 关联单据ID（调拨单/退货单） */
    private Long relatedId;

    private Long operatorId;

    /** PENDING=待入库，PROCESSING=入库中，DONE=已完成，CANCELLED=已取消 */
    private String status;

    private Date expectedTime;
    private Date actualTime;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
