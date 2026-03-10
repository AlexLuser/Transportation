package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 仓库商品关联实体类
 * 对应数据库表：warehouse_product
 * 用于存储商品在仓库中的库存信息（多对多关系）
 */
@Data
@TableName("warehouse_product")
public class WarehouseProduct {
    /** 关联ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 关联warehouse表的id（仓库ID） */
    private Long warehouseId;
    
    /** 关联product_info表的id（商品ID） */
    private Long productId;
    
    /** 该商品在该仓库的库存数量 */
    private Integer stock;
    
    /** 创建时间 */
    private Date createTime;
    
    /** 更新时间 */
    private Date updateTime;
}

