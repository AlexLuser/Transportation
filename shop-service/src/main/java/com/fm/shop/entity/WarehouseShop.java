package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 仓库-商家关联（共享仓多对多）
 * role: OWNER=所有者，TENANT=租用方
 */
@Data
@TableName("warehouse_shop")
public class WarehouseShop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    private Long shopId;

    /** OWNER=仓库所有者，TENANT=租用方 */
    private String role;

    /** 0=停用，1=启用 */
    private Integer status;

    private Date createTime;
    private Date updateTime;
}
