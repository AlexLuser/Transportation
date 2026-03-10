package com.fm.driver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 运输员信息实体类
 * 对应数据库表：driver_info
 */
@Data
@TableName("driver_info")
public class Driver {
    /** 运输员ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 关联user表的id（运输员用户） */
    private Long userId;
    
    /** 真实姓名 */
    private String realName;
    
    /** 手机号 */
    private String phone;
    
    /** 邮箱 */
    private String email;
    
    /** 身份证号 */
    private String idCard;
    
    /** 性别：0=未知，1=男，2=女 */
    private Integer gender;
    
    /** 生日 */
    private Date birthday;
    
    /** 头像URL */
    private String avatar;
    
    /** 驾驶证号 */
    private String licenseNumber;
    
    /** 驾驶证类型（C1/C2/B1/B2等） */
    private String licenseType;
    
    /** 驾驶证到期日期 */
    private Date licenseExpireDate;
    
    /** 状态：0=禁用，1=启用，2=待审核 */
    private Integer status;
    
    /** 创建时间 */
    private Date createTime;
    
    /** 更新时间 */
    private Date updateTime;
}

