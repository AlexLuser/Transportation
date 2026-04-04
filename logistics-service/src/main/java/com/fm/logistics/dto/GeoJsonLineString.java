package com.fm.logistics.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

public class GeoJsonLineString {

    private final String type = "LineString";

    private final List<double[]> coordinates = new ArrayList<>();

    private static final ObjectMapper mapper = new ObjectMapper();

    /*添加坐标点*/
    public GeoJsonLineString addPoint(double lat, double lng) {
        coordinates.add(new double[] { lng, lat });
        return this;
    }

    /*获取坐标点数量*/
    public int size() {
        return coordinates.size();
    }

    /*转换为JSON字符串*/
    public String toJsonString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }

    public String getType() {
        return type;
    }

    public List<double[]> getCoordinates() {
        return coordinates;
    }
}
