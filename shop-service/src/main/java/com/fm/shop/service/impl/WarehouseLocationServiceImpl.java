package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.shop.entity.WarehouseLocation;
import com.fm.shop.mapper.WarehouseLocationMapper;
import com.fm.shop.service.WarehouseLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WarehouseLocationServiceImpl extends ServiceImpl<WarehouseLocationMapper, WarehouseLocation>
        implements WarehouseLocationService {

    @Autowired
    private WarehouseLocationMapper locationMapper;

    @Override
    public List<WarehouseLocation> getByWarehouseId(Long warehouseId) {
        return locationMapper.selectList(new LambdaQueryWrapper<WarehouseLocation>()
                .eq(WarehouseLocation::getWarehouseId, warehouseId)
                .orderByAsc(WarehouseLocation::getZoneCode, WarehouseLocation::getLocationCode));
    }

    @Override
    public List<WarehouseLocation> getByZone(Long warehouseId, String zoneCode) {
        return locationMapper.selectList(new LambdaQueryWrapper<WarehouseLocation>()
                .eq(WarehouseLocation::getWarehouseId, warehouseId)
                .eq(WarehouseLocation::getZoneCode, zoneCode)
                .orderByAsc(WarehouseLocation::getLocationCode));
    }

    @Override
    public List<Map<String, Object>> getZoneSummary(Long warehouseId) {
        return locationMapper.selectZoneSummary(warehouseId);
    }

    @Override
    public WarehouseLocation saveLocation(WarehouseLocation location) {
        if (location.getId() == null) {
            locationMapper.insert(location);
        } else {
            locationMapper.updateById(location);
        }
        return location;
    }

    @Override
    public boolean deleteLocation(Long locationId) {
        return locationMapper.deleteById(locationId) > 0;
    }

    @Override
    public boolean updateStock(Long locationId, int delta) {
        LambdaUpdateWrapper<WarehouseLocation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(WarehouseLocation::getId, locationId)
                .setSql("current_stock = current_stock + " + delta);
        return locationMapper.update(null, wrapper) > 0;
    }
}
