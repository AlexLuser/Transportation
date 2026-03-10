package com.fm.logistics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fm.logistics.dto.CreateRouteRequestDTO;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.entity.LogisticsRoute;

import java.util.List;

/**
 * 物流路线服务接口
 */
public interface LogisticsRouteService extends IService<LogisticsRoute> {

    /**
     * 创建物流路线（由 order-service 发货时调用）
     */
    LogisticsRoute createRoute(CreateRouteRequestDTO request);

    /**
     * 根据订单ID获取路线详情
     */
    RouteDetailDTO getRouteDetailByOrderId(Long orderId);

    /**
     * 根据路线ID获取路线详情
     */
    RouteDetailDTO getRouteDetailById(Long routeId);

    /**
     * 根据路线编号获取路线详情
     */
    RouteDetailDTO getRouteDetailByRouteNo(String routeNo);

    /**
     * 绑定运输员（接单时由 driver-service 或管理员调用）
     */
    LogisticsRoute bindDriver(Long routeId, Long driverId, Long deliveryId);

    /**
     * 更新路线状态
     */
    LogisticsRoute updateRouteStatus(Long routeId, Integer routeStatus);

    /**
     * 更新路线的计划路线（GeoJSON）
     */
    LogisticsRoute updatePlannedRoute(Long routeId, String plannedRouteGeoJson);

    /**
     * 更新 AI 建议路线和分析结果
     */
    LogisticsRoute updateAIRoute(Long routeId, String aiSuggestedRouteGeoJson, String aiAnalysis);

    /**
     * 获取待出发路线列表（调度用）
     */
    List<LogisticsRoute> getPendingRoutes();

    /**
     * 获取运输员当前负责的路线列表
     */
    List<LogisticsRoute> getRoutesByDriverId(Long driverId);
}

