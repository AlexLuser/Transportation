package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
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
        validateLocationBounds(location);
        if (location.getId() == null) {
            locationMapper.insert(location);
        } else {
            locationMapper.updateById(location);
        }
        return location;
    }

    private void validateLocationBounds(WarehouseLocation loc) {
        int cap = loc.getCapacity() == null ? 0 : loc.getCapacity();
        int cur = loc.getCurrentStock() == null ? 0 : loc.getCurrentStock();
        if (cap < 0) {
            throw new BusinessException(ResultCode.FAIL, "库位容量不能为负数");
        }
        if (cur < 0) {
            throw new BusinessException(ResultCode.FAIL, "库位占用不能为负数");
        }
        if (cap > 0 && cur > cap) {
            throw new BusinessException(ResultCode.FAIL,
                    "库位占用不能超过容量（库位编码 " + loc.getLocationCode() + "，容量 " + cap + "，占用 " + cur + "）");
        }
    }

    @Override
    public boolean deleteLocation(Long locationId) {
        WarehouseLocation loc = locationMapper.selectById(locationId);
        if (loc == null) {
            return false;
        }
        int cur = loc.getCurrentStock() == null ? 0 : loc.getCurrentStock();
        if (cur > 0) {
            throw new BusinessException(ResultCode.FAIL,
                    "库位仍有占用（" + cur + "），无法删除：" + loc.getLocationCode());
        }
        return locationMapper.deleteById(locationId) > 0;
    }

    @Override
    public void adjustStock(Long locationId, Long warehouseId, int delta) {
        if (locationId == null || warehouseId == null) {
            throw new BusinessException(ResultCode.FAIL, "库位或仓库参数无效");
        }
        if (delta == 0) {
            return;
        }
        int n = locationMapper.adjustCurrentStockBounded(locationId, warehouseId, delta);
        if (n > 0) {
            return;
        }
        WarehouseLocation loc = locationMapper.selectById(locationId);
        if (loc == null || !warehouseId.equals(loc.getWarehouseId())) {
            throw new BusinessException(ResultCode.FAIL, "库位不存在或不属于当前仓库");
        }
        int cur = loc.getCurrentStock() == null ? 0 : loc.getCurrentStock();
        int cap = loc.getCapacity() == null ? 0 : loc.getCapacity();
        if (loc.getStatus() == null || loc.getStatus() != 1) {
            throw new BusinessException(ResultCode.FAIL,
                    "库位未处于正常状态，无法调整占用：" + loc.getLocationCode());
        }
        if (delta < 0 && cur + delta < 0) {
            throw new BusinessException(ResultCode.FAIL,
                    "库位占用不足，无法出库（库位 " + loc.getLocationCode() + "，当前占用 " + cur + "，需扣 " + (-delta) + "）");
        }
        if (delta > 0 && cap > 0 && cur + delta > cap) {
            throw new BusinessException(ResultCode.FAIL,
                    "超过库位容量（库位 " + loc.getLocationCode() + "，容量 " + cap + "，当前 " + cur + "，拟入库 " + delta + "）");
        }
        throw new BusinessException(ResultCode.FAIL, "库位占用调整失败：" + loc.getLocationCode());
    }
}
