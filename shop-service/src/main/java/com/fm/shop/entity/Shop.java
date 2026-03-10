package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 商户信息实体类
 * 对应数据库表：shop_info
 */
@Data
@TableName("shop_info")
public class Shop {
    /** 商户ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 关联user表的id（商户用户） */
    private Long userId;
    
    /** 商户名称 */
    private String shopName;
    
    /** 商户联系电话 */
    private String shopPhone;
    
    /** 商户邮箱 */
    private String shopEmail;
    
    /** 商户描述 */
    private String description;
    
    /** 商户Logo URL */
    private String logo;
    
    /** 营业执照号 */
    private String businessLicense;
    
    /** 状态：0=禁用，1=启用，2=待审核 */
    private Integer status;
    
    /** 创建时间 */
    private Date createTime;
    
    /** 更新时间 */
    private Date updateTime;
}

