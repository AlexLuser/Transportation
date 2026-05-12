package com.fm.shop.controller;

import com.fm.common.result.Result;
import com.fm.shop.entity.WarehouseShop;
import com.fm.shop.service.WarehouseShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "共享仓管理", description = "仓库与商家多对多关联接口")
@RestController
@RequestMapping("/api/warehouse-shops")
public class WarehouseShopController {

    @Autowired
    private WarehouseShopService warehouseShopService;

    @Operation(summary = "查询商家的仓库列表")
    @GetMapping("/shop/{shopId}/warehouses")
    public Result<List<Map<String, Object>>> getWarehousesByShop(@PathVariable Long shopId) {
        return Result.success(warehouseShopService.getWarehousesByShopId(shopId));
    }

    @Operation(summary = "查询仓库关联的商家列表")
    @GetMapping("/warehouse/{warehouseId}/shops")
    public Result<List<Map<String, Object>>> getShopsByWarehouse(@PathVariable Long warehouseId) {
        return Result.success(warehouseShopService.getShopsByWarehouseId(warehouseId));
    }

    @Operation(summary = "为仓库绑定商家")
    @PostMapping("/bind")
    public Result<WarehouseShop> bind(@RequestParam Long warehouseId,
                                      @RequestParam Long shopId,
                                      @RequestParam(defaultValue = "TENANT") String role) {
        return Result.success(warehouseShopService.bindShop(warehouseId, shopId, role));
    }

    @Operation(summary = "解绑商家-仓库关联")
    @DeleteMapping("/unbind")
    public Result<Boolean> unbind(@RequestParam Long warehouseId, @RequestParam Long shopId) {
        return Result.success(warehouseShopService.unbindShop(warehouseId, shopId));
    }
}
