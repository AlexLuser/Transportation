package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.shop.entity.WarehouseProduct;
import com.fm.shop.mapper.WarehouseProductMapper;
import com.fm.shop.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 库存服务实现类
 * 提供商品库存的业务逻辑实现
 */
@Service
public class StockServiceImpl extends ServiceImpl<WarehouseProductMapper, WarehouseProduct> implements StockService {
    @Autowired
    private WarehouseProductMapper warehouseProductMapper;

    @Override
    public List<WarehouseProduct> getStockByProductId(Long productId) {
        LambdaQueryWrapper<WarehouseProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseProduct::getProductId, productId);
        return warehouseProductMapper.selectList(wrapper);
    }

    @Override
    public List<WarehouseProduct> getStockByWarehouseId(Long warehouseId) {
        LambdaQueryWrapper<WarehouseProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseProduct::getWarehouseId, warehouseId);
        return warehouseProductMapper.selectList(wrapper);
    }

    @Override
    public WarehouseProduct getStock(Long warehouseId, Long productId) {
        LambdaQueryWrapper<WarehouseProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseProduct::getWarehouseId, warehouseId)
                .eq(WarehouseProduct::getProductId, productId);
        return warehouseProductMapper.selectOne(wrapper);
    }

    @Override
    public WarehouseProduct updateStock(Long warehouseId, Long productId, Integer stock) {
        // 查询是否已存在库存记录
        WarehouseProduct warehouseProduct = getStock(warehouseId, productId);
        if (warehouseProduct == null) {
            // 不存在则创建新记录
            warehouseProduct = new WarehouseProduct();
            warehouseProduct.setWarehouseId(warehouseId);
            warehouseProduct.setProductId(productId);
            warehouseProduct.setStock(stock);
            warehouseProductMapper.insert(warehouseProduct);
        } else {
            // 存在则更新库存数量
            warehouseProduct.setStock(stock);
            warehouseProductMapper.updateById(warehouseProduct);
        }
        return warehouseProduct;
    }

    @Override
    public WarehouseProduct addStock(Long warehouseId, Long productId, Integer stock) {
        // 查询是否已存在库存记录
        WarehouseProduct warehouseProduct = getStock(warehouseId, productId);
        if (warehouseProduct == null) {
            // 不存在则创建新记录
            warehouseProduct = new WarehouseProduct();
            warehouseProduct.setWarehouseId(warehouseId);
            warehouseProduct.setProductId(productId);
            warehouseProduct.setStock(stock);
            warehouseProductMapper.insert(warehouseProduct);
        } else {
            // 存在则增加库存数量
            warehouseProduct.setStock(warehouseProduct.getStock() + stock);
            warehouseProductMapper.updateById(warehouseProduct);
        }
        return warehouseProduct;
    }

    @Override
    public boolean deleteStock(Long warehouseId, Long productId) {
        LambdaQueryWrapper<WarehouseProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseProduct::getWarehouseId, warehouseId)
                .eq(WarehouseProduct::getProductId, productId);
        return warehouseProductMapper.delete(wrapper) > 0;
    }

    @Override
    public boolean deductStock(Long warehouseId, Long productId, Integer quantity) {
        if (warehouseId == null || productId == null || quantity == null || quantity <= 0) {
            return false;
        }
        // 使用原子操作扣减库存，如果库存不足则返回0
        int affectedRows = warehouseProductMapper.deductStock(warehouseId, productId, quantity);
        return affectedRows > 0;
    }
}

