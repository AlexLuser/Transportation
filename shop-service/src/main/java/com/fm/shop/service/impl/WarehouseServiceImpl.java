package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.common.geo.AmapGeocodingService;
import com.fm.common.geo.GeoPoint;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.shop.entity.Warehouse;
import com.fm.shop.entity.WarehouseProduct;
import com.fm.shop.entity.WarehouseShop;
import com.fm.shop.mapper.WarehouseMapper;
import com.fm.shop.mapper.WarehouseProductMapper;
import com.fm.shop.mapper.WarehouseShopMapper;
import com.fm.shop.service.WarehouseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 仓库服务实现类
 * 提供仓库信息的业务逻辑实现
 */
@Service
public class WarehouseServiceImpl extends ServiceImpl<WarehouseMapper, Warehouse> implements WarehouseService {
    @Autowired
    private WarehouseMapper warehouseMapper;

    @Autowired
    private WarehouseProductMapper warehouseProductMapper;

    @Autowired
    private WarehouseShopMapper warehouseShopMapper;

    @Autowired
    private AmapGeocodingService amapGeocodingService;

    @Override
    @Cacheable(value = "warehouseAll", key = "'all'")
    public List<Warehouse> getAllWarehouses() {
        return warehouseMapper.selectList(null);
    }

    @Override
    @Cacheable(value = "warehouseById", key = "#warehouseId", unless = "#result == null")
    public Warehouse getWarehouseById(Long warehouseId) {
        return warehouseMapper.selectById(warehouseId);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "warehouseById", key = "#warehouse.id", condition = "#warehouse.id != null"),
        @CacheEvict(value = "warehouseAll",  key = "'all'")
    })
    public Warehouse saveOrUpdateWarehouse(Warehouse warehouse) {
        Warehouse existing = warehouse.getId() != null ? getWarehouseById(warehouse.getId()) : null;
        if (warehouse.getCapacity() != null && warehouse.getCapacity() < 0) {
            throw new BusinessException(ResultCode.FAIL, "仓库容量不能为负数");
        }
        if (warehouse.getId() != null && warehouse.getCapacity() != null && warehouse.getCapacity() > 0) {
            int sum = warehouseProductMapper.sumStockByWarehouse(warehouse.getId());
            if (sum > warehouse.getCapacity()) {
                throw new BusinessException(ResultCode.FAIL,
                        "仓内总库存 " + sum + " 已超过拟定容量 " + warehouse.getCapacity() + "，请先调整库存");
            }
        }
        applyGeocode(warehouse, existing);
        if (warehouse.getId() == null) {
            warehouseMapper.insert(warehouse);
        } else {
            warehouseMapper.updateById(warehouse);
        }
        return warehouse;
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "warehouseById", key = "#warehouseId"),
        @CacheEvict(value = "warehouseAll",  key = "'all'")
    })
    public boolean updateWarehouseCapacity(Long warehouseId, Integer capacity) {
        if (capacity != null && capacity < 0) {
            throw new BusinessException(ResultCode.FAIL, "仓库容量不能为负数");
        }
        if (capacity != null && capacity > 0) {
            int sum = warehouseProductMapper.sumStockByWarehouse(warehouseId);
            if (sum > capacity) {
                throw new BusinessException(ResultCode.FAIL,
                        "当前仓内总库存 " + sum + "，不能将容量调整为 " + capacity);
            }
        }
        LambdaUpdateWrapper<Warehouse> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Warehouse::getId, warehouseId).set(Warehouse::getCapacity, capacity);
        return warehouseMapper.update(null, wrapper) > 0;
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "warehouseById", key = "#warehouseId"),
        @CacheEvict(value = "warehouseAll",  key = "'all'")
    })
    public boolean deleteWarehouse(Long warehouseId) {
        Long stockRows = warehouseProductMapper.selectCount(new LambdaQueryWrapper<WarehouseProduct>()
                .eq(WarehouseProduct::getWarehouseId, warehouseId)
                .gt(WarehouseProduct::getStock, 0));
        if (stockRows != null && stockRows > 0) {
            throw new BusinessException(ResultCode.FAIL, "仓库仍存在库存，无法删除");
        }
        Long bindings = warehouseShopMapper.selectCount(new LambdaQueryWrapper<WarehouseShop>()
                .eq(WarehouseShop::getWarehouseId, warehouseId)
                .eq(WarehouseShop::getStatus, 1));
        if (bindings != null && bindings > 0) {
            throw new BusinessException(ResultCode.FAIL, "仓库仍有关联商家，请先解绑后再删除");
        }
        return warehouseMapper.deleteById(warehouseId) > 0;
    }

    private void applyGeocode(Warehouse incoming, Warehouse existing) {
        String p = pick(incoming.getProvince(), existing != null ? existing.getProvince() : null);
        String c = pick(incoming.getCity(), existing != null ? existing.getCity() : null);
        String d = pick(incoming.getDistrict(), existing != null ? existing.getDistrict() : null);
        String detail = pick(incoming.getDetailAddress(), existing != null ? existing.getDetailAddress() : null);
        Optional<GeoPoint> geo = amapGeocodingService.geocode(p, c, d, detail);
        if (geo.isPresent()) {
            GeoPoint pt = geo.get();
            incoming.setLatitude(pt.latitude());
            incoming.setLongitude(pt.longitude());
        } else if (existing != null) {
            if (incoming.getLatitude() == null) {
                incoming.setLatitude(existing.getLatitude());
            }
            if (incoming.getLongitude() == null) {
                incoming.setLongitude(existing.getLongitude());
            }
        }
    }

    private static String pick(String incoming, String existing) {
        return incoming != null ? incoming : existing;
    }
}

