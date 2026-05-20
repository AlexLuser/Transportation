package com.fm.logistics.dto;

import lombok.Data;

/**
 * 全国 Hub 信息（含负载率，供前端地图渲染）
 */
@Data
public class NationalHubDTO {

    private Long id;
    private String name;
    private Integer hubLevel;
    private String province;
    private String city;
    private Double latitude;
    private Double longitude;
    private Integer maxCapacity;
    private Integer currentLoad;
    private Integer status;

    /** 负载率 = currentLoad / maxCapacity（静态字段，来自 national_hub 表） */
    private double loadRate;

    /** 当前在此 Hub 等待发车的货量（CREATED 批次 item_count 之和） */
    private int currentPendingLoad;

    /** 当前实时负载率 = currentPendingLoad / maxCapacity */
    private double currentPendingLoadRate;

    /** 今日经过此 Hub 的货物总量（含 CREATED/DEPARTED/ARRIVED 批次） */
    private int todayMaxLoad;

    /** 今日预计最大负载率 = todayMaxLoad / maxCapacity */
    private double todayMaxLoadRate;
}
