package com.fm.logistics.dto;

import lombok.Data;

/**
 * 中转站信息 DTO
 */
@Data
public class HubDTO {
    private Long id;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private String region;
    private Integer maxCapacity;
    private Integer currentLoad;
    /** 状态：0=正常，1=满载，2=关闭 */
    private Integer status;
    private String remark;
    /** 距离参考点的距离（km），查询最近Hub时填充 */
    private Double distanceKm;
}
