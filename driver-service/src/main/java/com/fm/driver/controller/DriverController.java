package com.fm.driver.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.driver.entity.Driver;
import com.fm.driver.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 运输员信息管理Controller
 */
@Tag(name = "运输员管理", description = "运输员信息相关接口")
@RestController
@RequestMapping("/api/drivers")
public class DriverController {
    
    @Autowired
    private DriverService driverService;
    
    /**
     * 获取当前运输员信息
     */
    @Operation(summary = "获取当前运输员信息", description = "根据当前登录用户获取运输员信息")
    @GetMapping
    public Result<Driver> getDriver(
            @RequestHeader(value = "userId", required = false) String userIdHeader) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在，请先完善信息");
        }
        return Result.success(driver);
    }
    
    /**
     * 获取指定运输员信息（管理员）
     */
    @Operation(summary = "获取指定运输员信息", description = "根据运输员ID获取信息（管理员）")
    @GetMapping("/{id}")
    public Result<Driver> getDriverById(
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "运输员ID", required = true)
            @PathVariable Long id) {
        // 只有管理员可以查看其他运输员信息
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        Driver driver = driverService.getDriverById(id);
        if (driver == null) {
            return Result.error("运输员信息不存在");
        }
        return Result.success(driver);
    }
    
    /**
     * 【内部接口】根据 userId 查询运输员信息
     * GET /api/drivers/internal/user/{userId}
     * 仅供服务间调用，根据 user.id 返回运输员业务主体信息
     */
    @Operation(summary = "内部：根据userId查询运输员", description = "服务间内部调用，根据 user.id 返回运输员业务主体信息")
    @GetMapping("/internal/user/{userId}")
    public Result<Driver> getDriverByUserId(
            @Parameter(description = "用户ID（user.id）", required = true)
            @PathVariable Long userId) {
        Driver driver = driverService.getDriverByUserId(userId);
        if (driver == null) {
            return Result.error("运输员信息不存在");
        }
        return Result.success(driver);
    }

    /**
     * 【内部接口】根据 driverId 查询运输员信息
     * GET /api/drivers/internal/{driverId}
     * 仅供服务间调用，根据 driver.id 返回运输员信息（如姓名、电话）
     */
    @Operation(summary = "内部：根据driverId查询运输员", description = "服务间内部调用，根据 driver.id 返回运输员基本信息")
    @GetMapping("/internal/{driverId}")
    public Result<Driver> getDriverByDriverId(
            @Parameter(description = "运输员ID（driver.id）", required = true)
            @PathVariable Long driverId) {
        Driver driver = driverService.getDriverById(driverId);
        if (driver == null) {
            return Result.error("运输员信息不存在");
        }
        return Result.success(driver);
    }

    /**
     * 获取所有运输员列表（管理员调度用）
     */
    @Operation(summary = "获取所有运输员列表", description = "管理员调度时选择司机使用")
    @GetMapping("/admin/list")
    public Result<List<Driver>> listAllDrivers(
            @RequestHeader(value = "roleCode", required = false) String roleCode) {
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问");
        }
        return Result.success(driverService.getAllDrivers());
    }

    /**
     * 添加运输员信息
     */
    @Operation(summary = "添加运输员信息", description = "添加新的运输员信息")
    @PostMapping
    public Result<Driver> addDriver(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Driver driver) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        // 检查是否已存在
        Driver existingDriver = driverService.getDriverByUserId(userId);
        if (existingDriver != null) {
            return Result.error("运输员信息已存在，请使用PUT方法更新");
        }
        
        driver.setUserId(userId);
        driver = driverService.saveOrUpdateDriver(driver);
        return Result.success(driver);
    }
    
    /**
     * 更新运输员信息
     */
    @Operation(summary = "更新运输员信息", description = "更新运输员信息")
    @PutMapping
    public Result<Driver> updateDriver(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Driver driver) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        // 获取现有记录
        Driver existingDriver = driverService.getDriverByUserId(userId);
        if (existingDriver == null) {
            return Result.error("运输员信息不存在，请使用POST方法创建");
        }
        
        // 更新现有记录
        driver.setId(existingDriver.getId());
        driver.setUserId(userId);  // 确保userId不被修改
        driver = driverService.saveOrUpdateDriver(driver);
        return Result.success(driver);
    }
}







