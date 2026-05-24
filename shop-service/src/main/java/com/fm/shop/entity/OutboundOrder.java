package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 出库单
 * 记录货物从商家发货仓出库的完整流程（发货给物流/调拨/退货）
 */
@Data
@TableName("outbound_order")
public class OutboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 出库单号（OB+时间戳+4位随机） */
    private String orderNo;

    private Long warehouseId;

    private Long shopId;

    /** 目的类型：DELIVERY=配送出库，TRANSFER=调拨，RETURN=退货，SALES_ORDER=销售订单发货（仓级库存已在下单时扣减） */
    private String destType;

    /** 关联单据ID（订单/调拨单） */
    private Long relatedId;

    private Long operatorId;

    /** PENDING=待出库，PROCESSING=出库中，DONE=已完成，CANCELLED=已取消 */
    private String status;

    private Date expectedTime;
    private Date actualTime;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
