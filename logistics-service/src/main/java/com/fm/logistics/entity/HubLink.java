package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Hub 间运输边（MCMF 网络的边）
 * transport_mode: ROAD / RAIL / AIR
 */
@Data
@TableName("hub_link")
public class HubLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromHubId;
    private Long toHubId;

    /** ROAD / RAIL / AIR */
    private String transportMode;

    /** 日最大运量（件） */
    private Integer capacityDaily;

    /** 今日有效费用（元/件，已含LLM校准） */
    private BigDecimal costPerUnit;

    /** 基准费用（静态录入值，LLM校准前） */
    private BigDecimal baseCostPerUnit;

    private Double distanceKm;
    private Double durationHours;

    /** 1=启用, 0=停用 */
    private Integer isActive;

    private Date createTime;
    private Date updateTime;
}
