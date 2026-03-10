package com.fm.shop.service;

import com.fm.shop.entity.WarehouseProduct;
import java.util.List;

/**
 * 库存服务接口
 * 提供商品库存的业务操作
 */
public interface StockService {
    /**
     * 根据商品ID获取该商品在所有仓库的库存
     * @param productId 商品ID
     * @return 库存列表
     */
    List<WarehouseProduct> getStockByProductId(Long productId);
    
    /**
     * 根据仓库ID获取该仓库所有商品的库存
     * @param warehouseId 仓库ID
     * @return 库存列表
     */
    List<WarehouseProduct> getStockByWarehouseId(Long warehouseId);
    
    /**
     * 获取指定仓库中指定商品的库存
     * @param warehouseId 仓库ID
     * @param productId 商品ID
     * @return 库存信息
     */
    WarehouseProduct getStock(Long warehouseId, Long productId);
    
    /**
     * 更新指定仓库中指定商品的库存数量
     * @param warehouseId 仓库ID
     * @param productId 商品ID
     * @param stock 库存数量
     * @return 更新后的库存信息
     */
    WarehouseProduct updateStock(Long warehouseId, Long productId, Integer stock);
    
    /**
     * 增加指定仓库中指定商品的库存数量
     * @param warehouseId 仓库ID
     * @param productId 商品ID
     * @param stock 增加的库存数量
     * @return 更新后的库存信息
     */
    WarehouseProduct addStock(Long warehouseId, Long productId, Integer stock);
    
    /**
     * 删除指定仓库中指定商品的库存记录
     * @param warehouseId 仓库ID
     * @param productId 商品ID
     * @return 是否删除成功
     */
    boolean deleteStock(Long warehouseId, Long productId);
    
    /**
     * 扣减指定仓库中指定商品的库存数量（原子操作）
     * @param warehouseId 仓库ID
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 是否扣减成功（库存不足时返回false）
     */
    boolean deductStock(Long warehouseId, Long productId, Integer quantity);
}

