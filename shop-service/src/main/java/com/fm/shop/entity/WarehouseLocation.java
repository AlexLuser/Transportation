package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 库位表
 * 表示仓库内部具体的存储位置，编码规则：区域-排-架-层
 */
@Data
@TableName("warehouse_location")
public class WarehouseLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    /** 区域编码（如 A、B、冷链区） */
    private String zoneCode;

    /** 排号 */
    private String rowNo;

    /** 架号 */
    private String shelfNo;

    /** 层号 */
    private String levelNo;

    /** 库位编码（唯一，如 A-01-02-03） */
    private String locationCode;

    /** 容量（件） */
    private Integer capacity;

    /** 当前占用量 */
    private Integer currentStock;

    /** 0=禁用，1=正常，2=锁定（盘点中） */
    private Integer status;

    private Date createTime;
    private Date updateTime;
}
