package com.fm.driver.service;

import com.fm.driver.entity.Driver;
import java.util.List;

/**
 * 运输员服务接口
 */
public interface DriverService {
    /**
     * 根据user_id获取运输员信息
     */
    Driver getDriverByUserId(Long userId);
    
    /**
     * 根据driver_id获取运输员信息
     */
    Driver getDriverById(Long driverId);
    
    /**
     * 创建或更新运输员信息
     */
    Driver saveOrUpdateDriver(Driver driver);
    
    /**
     * 获取所有运输员列表（管理员使用）
     */
    List<Driver> getAllDrivers();
    
    /**
     * 更新运输员状态
     */
    boolean updateDriverStatus(Long driverId, Integer status);
}

