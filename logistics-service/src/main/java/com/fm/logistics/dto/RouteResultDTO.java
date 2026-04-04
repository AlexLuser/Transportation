package com.fm.logistics.dto;

import lombok.Data;
import com.fm.logistics.dto.GeoJsonLineString;
import com.fm.logistics.dto.LlmDecisionResult;

@Data
public class RouteResultDTO {

    /*规划是否成功*/
    private boolean success;

    /*失败原因* (success == false)*/
    private String errorMsg;

    /*总距离*/
    private double distanceMeters;

    /*预计时间*/
    private long durationMs;

    /*路线坐标点*/
    private GeoJsonLineString routePoints;

    /*LLM 增强决策结果（llmEnhanced=true 时有值）*/
    private LlmDecisionResult llmDecision;

    /*是否经过 LLM 增强（false 表示降级为纯 A* 结果）*/
    private boolean llmEnhanced;
    
    /*成功构造方法*/
    public static RouteResultDTO success(double distanceMeters, long durationMs, GeoJsonLineString routePoints) {
        RouteResultDTO result = new RouteResultDTO();
        result.setSuccess(true);
        result.setDistanceMeters(distanceMeters);
        result.setDurationMs(durationMs);
        result.setRoutePoints(routePoints);
        return result;
    }

    /*失败构造方法*/
    public static RouteResultDTO error(String errorMsg) {
        RouteResultDTO result = new RouteResultDTO();
        result.setSuccess(false);
        result.setErrorMsg(errorMsg);
        return result;
    }

}
