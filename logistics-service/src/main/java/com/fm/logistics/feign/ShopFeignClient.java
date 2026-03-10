package com.fm.logistics.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 商户服务 Feign 客户端
 * 用于物流路线创建时获取仓库地址及经纬度信息
 */
@FeignClient(name = "shop-service")
public interface ShopFeignClient {

    /**
     * 获取仓库详情（包含 province/city/district/detailAddress/latitude/longitude）
     */
    @GetMapping("/api/warehouses/{id}")
    Result<Map<String, Object>> getWarehouseById(@PathVariable("id") Long warehouseId);
}

