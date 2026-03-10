package com.fm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 顾客信息实体类
 */
@Data
@TableName("customer_info")
public class Customer {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;        // 关联user表的id
    
    private String realName;    // 真实姓名
    
    private String phone;       // 手机号
    
    private String email;       // 邮箱
    
    private Integer gender;     // 性别：0=未知，1=男，2=女
    
    private Date birthday;      // 生日
    
    private String avatar;      // 头像URL
    
    private Integer status;     // 状态：0=禁用，1=启用
    
    private Date createTime;    // 创建时间
    
    private Date updateTime;    // 更新时间
}

