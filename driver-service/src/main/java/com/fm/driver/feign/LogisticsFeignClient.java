package com.fm.driver.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 物流服务 Feign 客户端（driver-service 侧）
 *
 * 用途：
 * 1. 司机接单后，根据 orderId 查找对应路线，再绑定司机和配送单
 * 2. 更新配送状态时，同步物流路线状态
 */
@FeignClient(name = "logistics-service")
public interface LogisticsFeignClient {

    /**
     * 根据订单ID查询物流路线详情
     * 返回 Map 包含 route / nodes / recentTracks / statusDesc 等字段
     * route.id 即为 routeId，driver-service 用它来调用 bind / updateStatus
     *
     * @param orderId  订单ID
     * @param userId   内部调用伪造的 userId，物流服务只检查非空即可
     */
    @GetMapping("/api/logistics/routes/order/{orderId}")
    Result<Map<String, Object>> getRouteByOrderId(
            @PathVariable("orderId") Long orderId,
            @RequestHeader("userId") String userId);

    /**
     * 绑定运输员到路线（接单时调用）
     * 请求体：{"driverId": x, "deliveryId": x}
     */
    @PutMapping("/api/logistics/routes/{routeId}/bind")
    Result<Map<String, Object>> bindDriver(
            @PathVariable("routeId") Long routeId,
            @RequestBody Map<String, Long> body);

    /**
     * 更新路线状态
     * 请求体：{"status": x}
     * 内部服务调用，roleCode 传 "driver" 即可通过鉴权
     */
    @PutMapping("/api/logistics/routes/{routeId}/status")
    Result<Map<String, Object>> updateRouteStatus(
            @PathVariable("routeId") Long routeId,
            @RequestBody Map<String, Integer> body,
            @RequestHeader("userId") String userId,
            @RequestHeader("roleCode") String roleCode);

    /**
     * 获取末端路线下所有停靠点（含 itemStatus）
     */
    @GetMapping("/api/logistics/batches/routes/{routeId}/stops")
    Result<List<Map<String, Object>>> getRouteStops(
            @PathVariable("routeId") Long routeId,
            @RequestHeader("userId") String userId);

    /**
     * 标记某停靠点（订单）已送达
     * 返回 {"allDone": true/false}
     */
    @PutMapping("/api/logistics/batches/routes/{routeId}/stops/{orderId}/complete")
    Result<Map<String, Object>> completeStop(
            @PathVariable("routeId") Long routeId,
            @PathVariable("orderId") Long orderId,
            @RequestHeader("userId") String userId);

    /**
     * 管理员预分配末端路线司机（服务间内部调用，传 roleCode="admin" 通过鉴权）
     * 请求体：{"driverId": x}
     * 将 driverId 写入 logistics_route，Hub 到达激活时自动转为已接单配送记录
     */
    @PutMapping("/api/logistics/routes/{routeId}/pre-assign")
    Result<Void> preAssignDriver(
            @PathVariable("routeId") Long routeId,
            @RequestBody Map<String, Long> body,
            @RequestHeader("roleCode") String roleCode);
}



