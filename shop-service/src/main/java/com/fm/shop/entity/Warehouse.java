package com.fm.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 仓库信息实体类
 * 对应数据库表：warehouse
 */
@Data
@TableName("warehouse")
public class Warehouse {
    /** 仓库ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 仓库名称 */
    private String warehouseName;
    
    /** 仓库联系电话 */
    private String warehousePhone;
    
    /** 省份 */
    private String province;
    
    /** 城市 */
    private String city;
    
    /** 区/县 */
    private String district;
    
    /** 详细地址 */
    private String detailAddress;
    
    /** 邮编 */
    private String postalCode;
    
    /** 仓库容量（单位：件/箱等，0表示无限制） */
    private Integer capacity;
    
    /** 状态：0=禁用，1=启用 */
    private Integer status;

    /** 纬度（用于物流路线规划，可选） */
    private Double latitude;

    /** 经度（用于物流路线规划，可选） */
    private Double longitude;

    /** 归属全国城市级配送中心ID（关联 national_hub.id） */
    private Long affiliatedHubId;

    /** 创建时间 */
    private Date createTime;
    
    /** 更新时间 */
    private Date updateTime;
}

