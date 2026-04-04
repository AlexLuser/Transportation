package com.fm.common.geo;

/**
 * 经纬度（WGS84 与 GCJ-02 由上层约定；高德返回为 GCJ-02）
 */
public record GeoPoint(double latitude, double longitude) {}
