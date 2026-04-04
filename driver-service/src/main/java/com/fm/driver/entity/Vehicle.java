package com.fm.driver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 车辆信息实体类
 * 对应数据库表：vehicle_info
 */
@Data
@TableName("vehicle_info")
public class Vehicle {
    /** 车辆ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 关联driver_info表的id */
    private Long driverId;
    
    /** 车辆类型：小型货车/中型货车/大型货车/厢式货车等 */
    private String vehicleType;
    
    /** 车辆品牌 */
    private String vehicleBrand;
    
    /** 车辆型号 */
    private String vehicleModel;
    
    /** 车牌号 */
    private String licensePlate;
    
    /** 载重（吨） */
    private BigDecimal loadCapacity;
    
    /** 载货体积（立方米） */
    private BigDecimal volumeCapacity;
    
    /** 车辆状态：0=停用，1=可用 */
    private Integer vehicleStatus;
    
    /** 创建时间 */
    private Date createTime;
    
    /** 更新时间 */
    private Date updateTime;
}
















