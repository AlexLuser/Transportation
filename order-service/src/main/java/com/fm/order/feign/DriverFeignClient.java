package com.fm.order.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 运输员服务Feign客户端
 * 用于调用driver-service的接口
 */
@FeignClient(name = "driver-service", path = "/api/drivers/deliveries")
public interface DriverFeignClient {
    
    /**
     * 创建配送记录（订单服务调用）
     * @param orderId 订单ID
     * @param deliveryAddress 配送地址
     * @param receiverName 收货人姓名
     * @param receiverPhone 收货人电话
     * @return 配送记录信息
     */
    @PostMapping("/create")
    Result<Map<String, Object>> createDelivery(
            @RequestParam("orderId") Long orderId,
            @RequestParam("deliveryAddress") String deliveryAddress,
            @RequestParam("receiverName") String receiverName,
            @RequestParam("receiverPhone") String receiverPhone);
}
















