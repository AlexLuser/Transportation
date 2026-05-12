package com.fm.logistics.service;

import com.fm.logistics.dto.CreateRouteRequestDTO;
import com.fm.logistics.dto.CreateRouteResponseDTO;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.entity.LogisticsRoute;

import java.util.List;

public interface LogisticsRouteService {

    /* 创建物流路线（单订单模式，兼容原有流程）*/
    CreateRouteResponseDTO createRoute(CreateRouteRequestDTO requestDTO);

    /**
     * 创建分段路线（批次模式专用）
     * 支持 segment_type=1（干线：仓库→Hub）和 segment_type=2（末端：Hub→客户）
     */
    LogisticsRoute createSegmentRoute(CreateRouteRequestDTO requestDTO);

    /**
     * 激活末端路线（Hub 到达后调用）
     * 将路线状态从"待激活(-1)"改为"待出发(0)"
     */
    void activateLastMileRoute(Long routeId);

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

    /* 展示路线信息 */
    List<LogisticsRoute> getPendingRoutes();

    /* 查看负责的路线 */
    List<LogisticsRoute> getRoutesByDriverId(Long driverId);

    /* 查询批次下所有路线段 */
    List<LogisticsRoute> getRoutesByBatchId(Long batchId);

    /**
     * 查询订单的完整物流全程（按时间顺序返回所有路线段）。
     * <p>
     * 包含：
     * <ol>
     *   <li>与 order_id 直接关联的路线（含干线 segmentType=3、旧体系直送路线）</li>
     *   <li>通过 logistics_batch_item 关联的城市末端路线（segmentType=1/2，多停靠共享路线）</li>
     * </ol>
     * 结果按创建时间升序，即从发货到最终到达的时间顺序。
     */
    List<RouteDetailDTO> getOrderJourney(Long orderId);
}
