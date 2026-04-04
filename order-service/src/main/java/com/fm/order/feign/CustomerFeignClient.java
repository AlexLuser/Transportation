package com.fm.order.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

/**
 * 顾客服务Feign客户端
 * 用于调用customer-service的接口
 * 注意：使用Map接收数据，避免直接依赖其他服务的Entity类
 */
@FeignClient(name = "customer-service", path = "/api/customers")
public interface CustomerFeignClient {
    
    /**
     * 根据顾客ID获取顾客信息
     * @param id 顾客ID
     * @param userIdHeader 用户ID（用于权限验证）
     * @param roleCode 角色代码
     * @return 顾客信息（使用Map避免直接依赖Entity类）
     */
    @GetMapping("/{id}")
    Result<Map<String, Object>> getCustomer(
            @PathVariable("id") Long id,
            @RequestHeader("userId") String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode
    );
    
    /**
     * 根据顾客ID获取地址列表
     * @param id 顾客ID
     * @param userIdHeader 用户ID（用于权限验证）
     * @param roleCode 角色代码
     * @return 地址列表（使用Map避免直接依赖Entity类）
     */
    @GetMapping("/{id}/address")
    Result<List<Map<String, Object>>> getAddresses(
            @PathVariable("id") Long id,
            @RequestHeader("userId") String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode
    );
    
    /**
     * 根据地址ID获取地址信息（订单服务调用，用于创建配送记录）
     * @param addressId 地址ID
     * @return 地址信息（使用Map避免直接依赖Entity类）
     */
    @GetMapping("/address/{addressId}")
    Result<Map<String, Object>> getAddressById(@PathVariable("addressId") Long addressId);

    /**
     * 【内部接口】根据 userId 查询顾客信息
     * 用于 userId → customerId 的身份转换，避免把 userId 直接当业务主键
     * @param userId user.id（网关注入）
     * @return 顾客业务主体信息（含 id = customerId）
     */
    @GetMapping("/internal/user/{userId}")
    Result<Map<String, Object>> getCustomerByUserId(@PathVariable("userId") Long userId);
}

