package com.fm.order.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 物流服务 Feign 客户端
 */
@FeignClient(name = "logistics-service")
public interface LogisticsFeignClient {

    /**
     * 创建物流路线（同城发货时调用）
     */
    @PostMapping("/api/logistics/routes")
    Result<Map<String, Object>> createRoute(@RequestBody Map<String, Object> request);

    /**
     * 根据发货仓库 + 收货坐标，分配 originHub / destHub
     * 请求体：{ warehouseId, endLat, endLng }
     * 返回：{ originHubId, destHubId, crossCity }
     */
    @PostMapping("/api/logistics/routing/assign-hubs")
    Result<Map<String, Object>> assignHubs(@RequestBody Map<String, Object> request);
}







