package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 调拨单
 * 商家在自己有权限的仓库之间进行货物调拨
 */
@Data
@TableName("transfer_order")
public class TransferOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调拨单号（TR+时间戳+4位随机） */
    private String orderNo;

    private Long shopId;

    private Long srcWarehouseId;

    private Long dstWarehouseId;

    private Long operatorId;

    /** PENDING=待审批，APPROVED=已审批，IN_TRANSIT=在途，DONE=已完成，CANCELLED=已取消 */
    private String status;

    private Date expectedTime;
    private Date actualTime;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
