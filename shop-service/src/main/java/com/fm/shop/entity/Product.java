package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品信息实体类
 * 对应数据库表：product_info
 */
@Data
@TableName("product_info")
public class Product {
    /** 商品ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 关联shop_info表的id（所属商户） */
    private Long shopId;
    
    /** 关联product_category表的id（商品分类） */
    private Long categoryId;
    
    /** 商品名称 */
    private String productName;
    
    /** 商品编码（SKU） */
    private String productCode;
    
    /** 商品描述 */
    private String description;
    
    /** 商品价格 */
    private BigDecimal price;
    
    /** 原价（用于显示折扣） */
    private BigDecimal originalPrice;
    
    /** 单位（件、箱、kg等） */
    private String unit;
    
    /** 重量（kg） */
    private BigDecimal weight;
    
    /** 商品图片（JSON数组格式，存储多个图片URL） */
    private String images;
    
    /** 状态：0=下架，1=上架，2=待审核 */
    private Integer status;
    
    /** 销量 */
    private Integer salesCount;
    
    /** 创建时间 */
    private Date createTime;
    
    /** 更新时间 */
    private Date updateTime;
}

