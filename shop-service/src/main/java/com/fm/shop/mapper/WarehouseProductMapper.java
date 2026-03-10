package com.fm.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.shop.entity.WarehouseProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 仓库商品关联Mapper接口
 * 提供仓库商品关联信息的数据库操作
 */
@Mapper
public interface WarehouseProductMapper extends BaseMapper<WarehouseProduct> {
    
    /**
     * 原子扣减库存（使用数据库原子操作，避免并发问题）
     * @param warehouseId 仓库ID
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 影响行数（如果库存不足，返回0）
     */
    @Update("UPDATE warehouse_product SET stock = stock - #{quantity} " +
            "WHERE warehouse_id = #{warehouseId} AND product_id = #{productId} " +
            "AND stock >= #{quantity}")
    int deductStock(@Param("warehouseId") Long warehouseId, 
                    @Param("productId") Long productId, 
                    @Param("quantity") Integer quantity);
}

