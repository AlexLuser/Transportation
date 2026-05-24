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

    /**
     * 调整库位占用（入库为正、出库为负）。校验归属仓库、容量上界与占用非负。
     */
    void adjustStock(Long locationId, Long warehouseId, int delta);
}
