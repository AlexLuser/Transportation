package com.fm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 收货地址实体类
 */
@Data
@TableName("customer_address")
public class Address {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long customerId;        // 关联customer_info表的id
    
    private String receiverName;    // 收货人姓名
    
    private String receiverPhone;   // 收货人电话
    
    private String province;        // 省份
    
    private String city;            // 城市
    
    private String district;        // 区/县
    
    private String detailAddress;   // 详细地址
    
    private String postalCode;      // 邮编
    
    private Integer isDefault;      // 是否默认地址：0=否，1=是

    private Double latitude;        // 收货地址纬度（用于物流路线规划终点）

    private Double longitude;       // 收货地址经度（用于物流路线规划终点）
    
    private Date createTime;        // 创建时间
    
    private Date updateTime;        // 更新时间
}

