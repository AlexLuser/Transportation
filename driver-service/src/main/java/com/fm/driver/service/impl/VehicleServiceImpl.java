package com.fm.driver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.driver.entity.Vehicle;
import com.fm.driver.mapper.VehicleMapper;
import com.fm.driver.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 车辆服务实现类
 */
@Service
public class VehicleServiceImpl implements VehicleService {
    
    @Autowired
    private VehicleMapper vehicleMapper;
    
    @Override
    public List<Vehicle> getVehiclesByDriverId(Long driverId) {
        LambdaQueryWrapper<Vehicle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Vehicle::getDriverId, driverId);
        wrapper.orderByDesc(Vehicle::getCreateTime);
        return vehicleMapper.selectList(wrapper);
    }
    
    @Override
    public Vehicle getVehicleById(Long vehicleId) {
        return vehicleMapper.selectById(vehicleId);
    }
    
    @Override
    public Vehicle saveVehicle(Vehicle vehicle) {
        vehicleMapper.insert(vehicle);
        return vehicle;
    }
    
    @Override
    public Vehicle updateVehicle(Vehicle vehicle) {
        vehicleMapper.updateById(vehicle);
        return vehicle;
    }
    
    @Override
    public boolean deleteVehicle(Long vehicleId) {
        return vehicleMapper.deleteById(vehicleId) > 0;
    }
    
    @Override
    public boolean updateVehicleStatus(Long vehicleId, Integer status) {
        LambdaUpdateWrapper<Vehicle> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Vehicle::getId, vehicleId)
                .set(Vehicle::getVehicleStatus, status);
        return vehicleMapper.update(null, wrapper) > 0;
    }
}

