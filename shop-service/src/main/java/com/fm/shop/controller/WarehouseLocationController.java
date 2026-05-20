package com.fm.shop.controller;

import com.fm.common.result.Result;
import com.fm.shop.entity.WarehouseLocation;
import com.fm.shop.service.WarehouseLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "库位管理", description = "仓库内库位管理接口")
@RestController
@RequestMapping("/api/warehouse-locations")
public class WarehouseLocationController {

    @Autowired
    private WarehouseLocationService locationService;

    @Operation(summary = "获取仓库所有库位")
    @GetMapping("/warehouse/{warehouseId}")
    public Result<List<WarehouseLocation>> getByWarehouse(@PathVariable Long warehouseId) {
        return Result.success(locationService.getByWarehouseId(warehouseId));
    }

    @Operation(summary = "按区域获取库位")
    @GetMapping("/warehouse/{warehouseId}/zone/{zoneCode}")
    public Result<List<WarehouseLocation>> getByZone(@PathVariable Long warehouseId,
                                                      @PathVariable String zoneCode) {
        return Result.success(locationService.getByZone(warehouseId, zoneCode));
    }

    @Operation(summary = "获取仓库库位区域汇总")
    @GetMapping("/warehouse/{warehouseId}/zone-summary")
    public Result<List<Map<String, Object>>> getZoneSummary(@PathVariable Long warehouseId) {
        return Result.success(locationService.getZoneSummary(warehouseId));
    }

    @Operation(summary = "新增或更新库位")
    @PostMapping
    public Result<WarehouseLocation> save(@RequestBody WarehouseLocation location) {
        return Result.success(locationService.saveLocation(location));
    }

    @Operation(summary = "更新库位")
    @PutMapping("/{id}")
    public Result<WarehouseLocation> update(@PathVariable Long id, @RequestBody WarehouseLocation location) {
        location.setId(id);
        return Result.success(locationService.saveLocation(location));
    }

    @Operation(summary = "删除库位")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(locationService.deleteLocation(id));
    }
}
