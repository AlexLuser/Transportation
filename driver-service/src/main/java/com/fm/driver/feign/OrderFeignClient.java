package com.fm.driver.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单服务 Feign 客户端（driver-service 侧）
 * 用于接单后同步订单为派送中、送达后同步为已完成等
 *
 * 注意：服务间直接调用不经过网关，
 *      因此需要手动在请求头中传入 userId 和 roleCode，
 *      否则 order-service 会直接返回 401/403。
 */
@FeignClient(name = "order-service", path = "/api/orders")
public interface OrderFeignClient {

    /**
     * 更新订单状态（内部调用）
     * roleCode 传 "admin" 以绕过商户身份校验，userId 传非空值即可通过鉴权
     *
     * @param orderId    订单ID
     * @param body       请求体，包含 orderStatus 字段
     * @param userId     伪造的 userId（任意非空值，仅用于通过非空校验）
     * @param roleCode   固定传 "admin"，使内部调用可跳过商户身份校验
     */
    @PutMapping("/{orderId}/status")
    Result<Boolean> updateOrderStatus(
            @PathVariable("orderId") Long orderId,
            @RequestBody Map<String, Integer> body,
            @RequestHeader("userId") String userId,
            @RequestHeader("roleCode") String roleCode
    );

    /**
     * 司机取消接单后，订单从派送中回到待揽件（与商户「发货」语义区分，避免误调用）
     */
    @PutMapping("/{orderId}/reopen-pickup")
    Result<Boolean> reopenOrderToPendingPickup(
            @PathVariable("orderId") Long orderId,
            @RequestHeader("userId") String userId,
            @RequestHeader("roleCode") String roleCode
    );
}
