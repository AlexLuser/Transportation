package com.fm.driver.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.driver.entity.Driver;
import com.fm.driver.entity.Vehicle;
import com.fm.driver.service.DriverService;
import com.fm.driver.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 车辆信息管理Controller
 */
@Tag(name = "车辆管理", description = "车辆信息相关接口")
@RestController
@RequestMapping("/api/drivers/vehicles")
public class VehicleController {
    
    @Autowired
    private VehicleService vehicleService;
    
    @Autowired
    private DriverService driverService;
    
    /**
     * 获取当前运输员的车辆列表
     */
    @Operation(summary = "获取车辆列表", description = "获取当前运输员的车辆列表")
    @GetMapping
    public Result<List<Vehicle>> getVehicles(
            @RequestHeader(value = "userId", required = false) String userIdHeader) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }
        
        List<Vehicle> vehicles = vehicleService.getVehiclesByDriverId(driver.getId());
        return Result.success(vehicles);
    }
    
    /**
     * 获取车辆详情
     */
    @Operation(summary = "获取车辆详情", description = "根据车辆ID获取车辆详情")
    @GetMapping("/{id}")
    public Result<Vehicle> getVehicle(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "车辆ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Vehicle vehicle = vehicleService.getVehicleById(id);
        if (vehicle == null) {
            return Result.error("车辆不存在");
        }
        
        // 验证权限：只能查看自己的车辆
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null || !vehicle.getDriverId().equals(driver.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        return Result.success(vehicle);
    }
    
    /**
     * 添加车辆
     */
    @Operation(summary = "添加车辆", description = "添加新的车辆信息")
    @PostMapping
    public Result<Vehicle> addVehicle(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Vehicle vehicle) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }
        
        vehicle.setDriverId(driver.getId());
        vehicle = vehicleService.saveVehicle(vehicle);
        return Result.success(vehicle);
    }
    
    /**
     * 更新车辆信息
     */
    @Operation(summary = "更新车辆信息", description = "更新车辆信息")
    @PutMapping
    public Result<Vehicle> updateVehicle(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Vehicle vehicle) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        if (vehicle.getId() == null) {
            return Result.error("车辆ID不能为空");
        }
        
        Vehicle existingVehicle = vehicleService.getVehicleById(vehicle.getId());
        if (existingVehicle == null) {
            return Result.error("车辆不存在");
        }
        
        // 验证权限
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null || !existingVehicle.getDriverId().equals(driver.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        vehicle.setDriverId(driver.getId());  // 确保driverId不被修改
        vehicle = vehicleService.updateVehicle(vehicle);
        return Result.success(vehicle);
    }
    
    /**
     * 删除车辆
     */
    @Operation(summary = "删除车辆", description = "根据车辆ID删除车辆")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteVehicle(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "车辆ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Vehicle vehicle = vehicleService.getVehicleById(id);
        if (vehicle == null) {
            return Result.error("车辆不存在");
        }
        
        // 验证权限
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null || !vehicle.getDriverId().equals(driver.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        boolean success = vehicleService.deleteVehicle(id);
        return Result.success(success);
    }
    
    /**
     * 更新车辆状态
     */
    @Operation(summary = "更新车辆状态", description = "更新车辆状态（可用/停用）")
    @PutMapping("/{id}/status")
    public Result<Boolean> updateVehicleStatus(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "车辆ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "车辆状态：0=停用，1=可用")
            @RequestParam("status") Integer status) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Vehicle vehicle = vehicleService.getVehicleById(id);
        if (vehicle == null) {
            return Result.error("车辆不存在");
        }
        
        // 验证权限
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null || !vehicle.getDriverId().equals(driver.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        boolean success = vehicleService.updateVehicleStatus(id, status);
        return Result.success(success);
    }
}

