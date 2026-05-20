package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.shop.entity.Product;
import com.fm.shop.entity.Warehouse;
import com.fm.shop.entity.WarehouseProduct;
import com.fm.shop.mapper.WarehouseMapper;
import com.fm.shop.mapper.WarehouseProductMapper;
import com.fm.shop.service.ProductService;
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

    @Autowired
    private WarehouseMapper warehouseMapper;

    @Autowired
    private ProductService productService;

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
        if (stock == null || stock < 0) {
            throw new BusinessException(ResultCode.FAIL, "库存数量不能为负数");
        }
        WarehouseProduct warehouseProduct = getStock(warehouseId, productId);
        int oldQty = warehouseProduct == null || warehouseProduct.getStock() == null ? 0 : warehouseProduct.getStock();
        int delta = stock - oldQty;
        assertCapacityAllows(warehouseId, delta);
        if (warehouseProduct == null) {
            warehouseProduct = new WarehouseProduct();
            warehouseProduct.setWarehouseId(warehouseId);
            warehouseProduct.setProductId(productId);
            warehouseProduct.setStock(stock);
            ensureShopIdOnRow(warehouseProduct, productId);
            warehouseProductMapper.insert(warehouseProduct);
        } else {
            ensureShopIdOnRow(warehouseProduct, productId);
            warehouseProduct.setStock(stock);
            warehouseProductMapper.updateById(warehouseProduct);
        }
        return warehouseProduct;
    }

    @Override
    public WarehouseProduct addStock(Long warehouseId, Long productId, Integer stock) {
        if (stock == null || stock <= 0) {
            throw new BusinessException(ResultCode.FAIL, "入库数量必须大于 0");
        }
        assertCapacityAllows(warehouseId, stock);
        WarehouseProduct warehouseProduct = getStock(warehouseId, productId);
        if (warehouseProduct == null) {
            warehouseProduct = new WarehouseProduct();
            warehouseProduct.setWarehouseId(warehouseId);
            warehouseProduct.setProductId(productId);
            warehouseProduct.setStock(stock);
            ensureShopIdOnRow(warehouseProduct, productId);
            warehouseProductMapper.insert(warehouseProduct);
        } else {
            ensureShopIdOnRow(warehouseProduct, productId);
            int base = warehouseProduct.getStock() != null ? warehouseProduct.getStock() : 0;
            warehouseProduct.setStock(base + stock);
            warehouseProductMapper.updateById(warehouseProduct);
        }
        return warehouseProduct;
    }

    /** capacity 为 null 或 0 视为不限制 */
    private void assertCapacityAllows(Long warehouseId, int stockDelta) {
        if (stockDelta <= 0 || warehouseId == null) {
            return;
        }
        Warehouse w = warehouseMapper.selectById(warehouseId);
        if (w == null || w.getCapacity() == null || w.getCapacity() <= 0) {
            return;
        }
        int currentSum = warehouseProductMapper.sumStockByWarehouse(warehouseId);
        if (currentSum + stockDelta > w.getCapacity()) {
            throw new BusinessException(ResultCode.FAIL,
                    "超过仓库容量上限（容量 " + w.getCapacity() + "，当前已占用 " + currentSum + "）");
        }
    }

    private void ensureShopIdOnRow(WarehouseProduct row, Long productId) {
        if (row.getShopId() != null || productId == null) {
            return;
        }
        Product p = productService.getProductById(productId);
        if (p != null) {
            row.setShopId(p.getShopId());
        }
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

