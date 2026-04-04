package com.fm.logistics.dto;

import lombok.Data;

/**
 * 运输员位置上报 DTO
 * 运输员 App 每 15~30 秒调用一次位置上报接口
 */
@Data
public class LocationUpdateDTO {

    /** 所属路线ID（必填） */
    private Long routeId;

    /** 纬度（必填） */
    private Double latitude;

    /** 经度（必填） */
    private Double longitude;

    /** 海拔，米（可选） */
    private Double altitude;

    /** 速度，km/h（可选） */
    private Double speed;

    /** 方向角，0=正北，顺时针（可选） */
    private Double heading;

    /** GPS 精度，米，值越小越精确（可选） */
    private Double accuracy;

    /**
     * 当前位置描述（可选）
     * 由客户端调用地图 SDK 逆地理编码后传入，避免服务端重复调用
     * 示例："上海市浦东新区世纪大道附近"
     */
    private String address;

    // trackTime 不由前端传入，由服务端接收时统一赋值，避免客户端时间不可信问题
}

