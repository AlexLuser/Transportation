package com.fm.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 司机注册信息（auth-service 内部用于插入 driver_info 表）
 */
@Data
@TableName("driver_info")
public class DriverRegistration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String realName;

    private String phone;

    private String email;

    private String licenseNumber;

    private String licenseType;

    private Date licenseExpireDate;

    /** 状态：0=禁用，1=启用，2=待审核 */
    private Integer status;
}
