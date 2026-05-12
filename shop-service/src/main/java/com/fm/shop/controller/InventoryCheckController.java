package com.fm.shop.controller;

import com.fm.common.result.Result;
import com.fm.shop.entity.InventoryCheck;
import com.fm.shop.entity.InventoryCheckItem;
import com.fm.shop.service.InventoryCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "盘点管理", description = "仓库盘点单接口")
@RestController
@RequestMapping("/api/inventory-checks")
public class InventoryCheckController {

    @Autowired
    private InventoryCheckService checkService;

    @Operation(summary = "查询盘点单列表")
    @GetMapping
    public Result<List<InventoryCheck>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String status) {
        return Result.success(checkService.listByWarehouse(warehouseId, shopId, status));
    }

    @Operation(summary = "获取盘点单详情")
    @GetMapping("/{id}")
    public Result<InventoryCheck> getById(@PathVariable Long id) {
        return Result.success(checkService.getById(id));
    }

    @Operation(summary = "获取盘点明细")
    @GetMapping("/{id}/items")
    public Result<List<InventoryCheckItem>> getItems(@PathVariable Long id) {
        return Result.success(checkService.getItems(id));
    }

    @Operation(summary = "获取盘点差异汇总")
    @GetMapping("/{id}/summary")
    public Result<Map<String, Object>> getSummary(@PathVariable Long id) {
        return Result.success(checkService.getCheckSummary(id));
    }

    @Operation(summary = "发起盘点")
    @PostMapping
    public Result<InventoryCheck> create(@RequestBody InventoryCheck check) {
        return Result.success(checkService.create(check));
    }

    @Operation(summary = "开始盘点")
    @PutMapping("/{id}/start")
    public Result<InventoryCheck> start(@PathVariable Long id) {
        return Result.success(checkService.startCheck(id));
    }

    @Operation(summary = "录入盘点结果")
    @PutMapping("/{id}/items/{itemId}")
    public Result<InventoryCheckItem> submitItem(@PathVariable Long id,
                                                  @PathVariable Long itemId,
                                                  @RequestParam Integer actualQty) {
        return Result.success(checkService.submitItemResult(id, itemId, actualQty));
    }

    @Operation(summary = "确认盘点并调整库存")
    @PutMapping("/{id}/confirm")
    public Result<InventoryCheck> confirm(@PathVariable Long id) {
        return Result.success(checkService.confirmAndAdjust(id));
    }
}
