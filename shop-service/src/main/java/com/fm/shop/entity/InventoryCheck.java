package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 盘点单
 * 支持全仓盘点、分区盘点、按商家范围盘点
 */
@Data
@TableName("inventory_check")
public class InventoryCheck {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单号（CC+时间戳+4位随机） */
    private String checkNo;

    private Long warehouseId;

    /** 商家ID（null=全仓盘点） */
    private Long shopId;

    /** FULL=全盘，ZONE=分区盘，DYNAMIC=动态盘 */
    private String checkType;

    /** 盘点区域（分区盘时有效） */
    private String zoneCode;

    private Long operatorId;

    /** PENDING=待盘点，PROCESSING=盘点中，CONFIRMING=待确认，DONE=已完成 */
    private String status;

    private Date startTime;
    private Date endTime;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
