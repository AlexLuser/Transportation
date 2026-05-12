package com.fm.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.shop.entity.WarehouseProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

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

    /**
     * 查询指定仓库的库存，并关联商品名称（不限商家）
     */
    @Select("SELECT wp.id, wp.warehouse_id AS warehouseId, wp.product_id AS productId, " +
            "wp.stock, p.product_name AS productName, p.unit " +
            "FROM warehouse_product wp " +
            "LEFT JOIN product_info p ON wp.product_id = p.id " +
            "WHERE wp.warehouse_id = #{warehouseId} " +
            "ORDER BY wp.stock DESC")
    List<Map<String, Object>> selectStockDetailByWarehouseId(@Param("warehouseId") Long warehouseId);

    /**
     * 查询指定仓库中指定商家的库存明细
     */
    @Select("SELECT wp.id, wp.warehouse_id AS warehouseId, wp.product_id AS productId, " +
            "wp.stock, p.product_name AS productName, p.unit " +
            "FROM warehouse_product wp " +
            "LEFT JOIN product_info p ON wp.product_id = p.id " +
            "WHERE wp.warehouse_id = #{warehouseId} AND wp.shop_id = #{shopId} " +
            "ORDER BY wp.stock DESC")
    List<Map<String, Object>> selectStockDetailByWarehouseAndShop(
            @Param("warehouseId") Long warehouseId,
            @Param("shopId") Long shopId);
}

