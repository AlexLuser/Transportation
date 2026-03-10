package com.fm.driver.service;

import com.fm.driver.entity.Vehicle;
import java.util.List;

/**
 * 车辆服务接口
 */
public interface VehicleService {
    /**
     * 根据driver_id获取车辆列表
     */
    List<Vehicle> getVehiclesByDriverId(Long driverId);
    
    /**
     * 根据vehicle_id获取车辆信息
     */
    Vehicle getVehicleById(Long vehicleId);
    
    /**
     * 创建车辆信息
     */
    Vehicle saveVehicle(Vehicle vehicle);
    
    /**
     * 更新车辆信息
     */
    Vehicle updateVehicle(Vehicle vehicle);
    
    /**
     * 删除车辆信息
     */
    boolean deleteVehicle(Long vehicleId);
    
    /**
     * 更新车辆状态
     */
    boolean updateVehicleStatus(Long vehicleId, Integer status);
}

