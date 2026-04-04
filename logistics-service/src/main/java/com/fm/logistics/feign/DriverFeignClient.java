package com.fm.logistics.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 运输员服务 Feign 客户端（logistics-service 侧）
 *
 * 用途：B6 轨迹上报时将 userId 转换为 driverId
 * 调用路径：GET /api/drivers/internal/user/{userId}
 */
@FeignClient(name = "driver-service")
public interface DriverFeignClient {

    /**
     * 【内部接口】根据 userId 查询运输员信息
     */
    @GetMapping("/api/drivers/internal/user/{userId}")
    Result<Map<String, Object>> getDriverByUserId(@PathVariable("userId") Long userId);

    /**
     * 【内部接口】根据 driverId 查询运输员信息（姓名、电话等）
     */
    @GetMapping("/api/drivers/internal/{driverId}")
    Result<Map<String, Object>> getDriverByDriverId(@PathVariable("driverId") Long driverId);
}



