package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 全国物流中转站（MCMF 网络节点）
 * hub_level: 0=全国枢纽, 1=省级中心, 2=城市配送中心
 */
@Data
@TableName("national_hub")
public class NationalHub {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 0=全国枢纽, 1=省级中心, 2=城市配送中心 */
    private Integer hubLevel;

    private String province;
    private String city;

    private Double latitude;
    private Double longitude;

    private Integer maxCapacity;
    private Integer currentLoad;

    /** 0=正常, 1=满载, 2=关闭 */
    private Integer status;

    private Date createTime;
    private Date updateTime;
}
