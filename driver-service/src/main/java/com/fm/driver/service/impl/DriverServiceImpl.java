package com.fm.driver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.driver.entity.Driver;
import com.fm.driver.mapper.DriverMapper;
import com.fm.driver.service.DriverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 运输员服务实现类
 */
@Service
public class DriverServiceImpl implements DriverService {
    
    @Autowired
    private DriverMapper driverMapper;
    
    @Override
    public Driver getDriverByUserId(Long userId) {
        LambdaQueryWrapper<Driver> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Driver::getUserId, userId);
        return driverMapper.selectOne(wrapper);
    }
    
    @Override
    public Driver getDriverById(Long driverId) {
        return driverMapper.selectById(driverId);
    }
    
    @Override
    public Driver saveOrUpdateDriver(Driver driver) {
        if (driver.getId() == null) {
            // 新增
            driverMapper.insert(driver);
        } else {
            // 更新
            driverMapper.updateById(driver);
        }
        return driver;
    }
    
    @Override
    public List<Driver> getAllDrivers() {
        return driverMapper.selectList(null);
    }
    
    @Override
    public boolean updateDriverStatus(Long driverId, Integer status) {
        LambdaUpdateWrapper<Driver> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Driver::getId, driverId)
                .set(Driver::getStatus, status);
        return driverMapper.update(null, wrapper) > 0;
    }
}

