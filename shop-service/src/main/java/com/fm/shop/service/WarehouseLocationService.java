package com.fm.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fm.shop.entity.WarehouseLocation;

import java.util.List;
import java.util.Map;

public interface WarehouseLocationService extends IService<WarehouseLocation> {

    List<WarehouseLocation> getByWarehouseId(Long warehouseId);

    List<WarehouseLocation> getByZone(Long warehouseId, String zoneCode);

    /** 按区域汇总库位占用情况 */
    List<Map<String, Object>> getZoneSummary(Long warehouseId);

    WarehouseLocation saveLocation(WarehouseLocation location);

    boolean deleteLocation(Long locationId);

    /** 更新库位占用量（入库/出库时调用） */
    boolean updateStock(Long locationId, int delta);
}
