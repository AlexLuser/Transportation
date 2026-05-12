package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.shop.entity.WarehouseShop;
import com.fm.shop.mapper.WarehouseShopMapper;
import com.fm.shop.service.WarehouseShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WarehouseShopServiceImpl extends ServiceImpl<WarehouseShopMapper, WarehouseShop>
        implements WarehouseShopService {

    @Autowired
    private WarehouseShopMapper warehouseShopMapper;

    @Override
    public List<Map<String, Object>> getWarehousesByShopId(Long shopId) {
        return warehouseShopMapper.selectWarehousesByShopId(shopId);
    }

    @Override
    public List<Map<String, Object>> getShopsByWarehouseId(Long warehouseId) {
        return warehouseShopMapper.selectShopsByWarehouseId(warehouseId);
    }

    @Override
    public boolean hasPermission(Long shopId, Long warehouseId) {
        LambdaQueryWrapper<WarehouseShop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseShop::getShopId, shopId)
                .eq(WarehouseShop::getWarehouseId, warehouseId)
                .eq(WarehouseShop::getStatus, 1);
        return warehouseShopMapper.selectCount(wrapper) > 0;
    }

    @Override
    public WarehouseShop bindShop(Long warehouseId, Long shopId, String role) {
        LambdaQueryWrapper<WarehouseShop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseShop::getWarehouseId, warehouseId)
                .eq(WarehouseShop::getShopId, shopId);
        WarehouseShop existing = warehouseShopMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setRole(role);
            existing.setStatus(1);
            warehouseShopMapper.updateById(existing);
            return existing;
        }
        WarehouseShop ws = new WarehouseShop();
        ws.setWarehouseId(warehouseId);
        ws.setShopId(shopId);
        ws.setRole(role);
        ws.setStatus(1);
        warehouseShopMapper.insert(ws);
        return ws;
    }

    @Override
    public boolean unbindShop(Long warehouseId, Long shopId) {
        LambdaQueryWrapper<WarehouseShop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseShop::getWarehouseId, warehouseId)
                .eq(WarehouseShop::getShopId, shopId);
        return warehouseShopMapper.delete(wrapper) > 0;
    }
}
