package com.fm.driver.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 订单服务Feign客户端
 * 用于调用order-service的接口
 */
@FeignClient(name = "order-service", path = "/api/orders")
public interface OrderFeignClient {
    
    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param body 请求体，包含orderStatus字段
     * @return 是否更新成功
     */
    @PutMapping("/{orderId}/status")
    Result<Boolean> updateOrderStatus(
            @PathVariable("orderId") Long orderId,
            @RequestBody Map<String, Integer> body
    );
}

