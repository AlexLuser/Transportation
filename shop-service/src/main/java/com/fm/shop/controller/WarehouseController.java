package com.fm.shop.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.shop.entity.Warehouse;
import com.fm.shop.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 仓库管理Controller
 * 提供仓库信息的RESTful API接口
 * 接口设计：
 * GET    /api/warehouses              - 获取仓库列表
 * GET    /api/warehouses/{id}         - 获取仓库详情
 * PUT    /api/warehouses/{id}/capacity - 修改仓库容量
 */
@Tag(name = "仓库管理", description = "仓库信息相关接口")
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {
    
    @Autowired
    private WarehouseService warehouseService;
    
    /**
     * 获取仓库列表
     * @return 仓库列表
     */
    @Operation(summary = "获取仓库列表", description = "获取所有仓库列表")
    @GetMapping
    public Result<List<Warehouse>> getWarehouses() {
        List<Warehouse> warehouses = warehouseService.getAllWarehouses();
        return Result.success(warehouses);
    }
    
    /**
     * 获取仓库详情
     * @param id 仓库ID
     * @return 仓库信息
     */
    @Operation(summary = "获取仓库详情", description = "根据仓库ID获取仓库详情")
    @GetMapping("/{id}")
    public Result<Warehouse> getWarehouse(
            @Parameter(description = "仓库ID", required = true)
            @PathVariable Long id) {
        Warehouse warehouse = warehouseService.getWarehouseById(id);
        if (warehouse == null) {
            return Result.error("仓库不存在");
        }
        return Result.success(warehouse);
    }
    
    /**
     * 修改仓库容量
     * @param userIdHeader 用户ID（从请求头获取）
     * @param id 仓库ID
     * @param capacity 容量（单位：件/箱等，0表示无限制）
     * @return 是否更新成功
     */
    @Operation(summary = "修改仓库容量", description = "修改指定仓库的容量")
    @PutMapping("/{id}/capacity")
    public Result<Boolean> updateWarehouseCapacity(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "仓库ID", required = true)
            @PathVariable Long id,
            @RequestParam(value = "capacity", required = true) Integer capacity) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        
        Warehouse warehouse = warehouseService.getWarehouseById(id);
        if (warehouse == null) {
            return Result.error("仓库不存在");
        }
        
        if (capacity < 0) {
            return Result.error("仓库容量不能为负数");
        }
        
        boolean success = warehouseService.updateWarehouseCapacity(id, capacity);
        return Result.success(success);
    }
}

