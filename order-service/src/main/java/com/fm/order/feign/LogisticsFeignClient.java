package com.fm.order.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 物流服务 Feign 客户端
 * 订单发货时调用，自动在 logistics-service 创建物流路线
 */
@FeignClient(name = "logistics-service")
public interface LogisticsFeignClient {

    /**
     * 创建物流路线
     * 请求体字段：orderId, warehouseId, startAddress, startLat(可选), startLng(可选),
     *             endAddress, endLat(可选), endLng(可选), receiverName, receiverPhone
     */
    @PostMapping("/api/logistics/routes")
    Result<Map<String, Object>> createRoute(@RequestBody Map<String, Object> request);
}

