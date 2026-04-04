package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.common.geo.AmapGeocodingService;
import com.fm.common.geo.GeoPoint;
import com.fm.shop.entity.Warehouse;
import com.fm.shop.mapper.WarehouseMapper;
import com.fm.shop.service.WarehouseService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private AmapGeocodingService amapGeocodingService;

    @Override
    public List<Warehouse> getAllWarehouses() {
        return warehouseMapper.selectList(null);
    }

    @Override
    public Warehouse getWarehouseById(Long warehouseId) {
        return warehouseMapper.selectById(warehouseId);
    }

    @Override
    public Warehouse saveOrUpdateWarehouse(Warehouse warehouse) {
        Warehouse existing = warehouse.getId() != null ? getWarehouseById(warehouse.getId()) : null;
        applyGeocode(warehouse, existing);
        if (warehouse.getId() == null) {
            warehouseMapper.insert(warehouse);
        } else {
            warehouseMapper.updateById(warehouse);
        }
        return warehouse;
    }

    @Override
    public boolean updateWarehouseCapacity(Long warehouseId, Integer capacity) {
        LambdaUpdateWrapper<Warehouse> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Warehouse::getId, warehouseId).set(Warehouse::getCapacity, capacity);
        return warehouseMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean deleteWarehouse(Long warehouseId) {
        // 注意：删除仓库前，应该先删除关联的库存信息
        // 这里只删除仓库信息，库存的级联删除需要在业务层处理
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

