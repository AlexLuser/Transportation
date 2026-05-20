package com.fm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 司机信息（user-service 内部用于审核状态查询与更新）
 */
@Data
@TableName("driver_info")
public class DriverReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String realName;

    private String phone;

    private String licenseNumber;

    private String licenseType;

    private Date licenseExpireDate;

    /** 状态：0=禁用，1=启用，2=待审核 */
    private Integer status;
}
