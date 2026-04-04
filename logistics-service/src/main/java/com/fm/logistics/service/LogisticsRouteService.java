package com.fm.logistics.service;

import com.fm.logistics.dto.CreateRouteRequestDTO;
import com.fm.logistics.dto.CreateRouteResponseDTO;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.entity.LogisticsRoute;

import java.util.List;

public interface LogisticsRouteService {

    /* 创建物流路线 */ 
    CreateRouteResponseDTO createRoute(CreateRouteRequestDTO requestDTO);

    /* 根据路线ID获取路线详情 */    
    RouteDetailDTO getRouteDetailById(Long routeId);

    /* 根据路线单号获取路线详情 */
    RouteDetailDTO getRouteDetailByRouteNo(String routeNo);

    /* 根据订单ID获取路线详情 */
    RouteDetailDTO getRouteDetailByOrderId(Long orderId);

    /* 绑定运输员 */
    LogisticsRoute bindDriver(Long routeId, Long driverId, Long deliveryId);

    /* 更新路线状态 */
    LogisticsRoute updateRouteStatus(Long routeId, Integer status);

    /*展示路线信息*/
    List<LogisticsRoute> getPendingRoutes();

    /* 查看负责的路线 */
    List<LogisticsRoute> getRoutesByDriverId(Long driverId);
}
