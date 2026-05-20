package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 仓库信息（logistics-service 只读副本，用于 ShipmentRoutingService）
 */
@Data
@TableName("warehouse")
public class Warehouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String warehouseName;
    private String province;
    private String city;
    private Double latitude;
    private Double longitude;

    /** 归属全国城市级配送中心ID */
    private Long affiliatedHubId;

    private Integer status;
}
