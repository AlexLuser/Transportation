package com.fm.order.feign;

import com.fm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 商户服务Feign客户端
 * 用于调用shop-service的接口
 * 注意：使用Map接收数据，避免直接依赖其他服务的Entity类
 */
@FeignClient(name = "shop-service")
public interface ShopFeignClient {

    /**
     * 根据商品ID获取商品信息
     * @param id 商品ID
     * @return 商品信息（使用Map避免直接依赖Entity类）
     */
    @GetMapping("/api/products/{id}")
    Result<Map<String, Object>> getProduct(@PathVariable("id") Long id);
    
    /**
     * 获取商品库存列表（所有仓库）
     * @param productId 商品ID
     * @return 库存列表（List<Map>）
     */
    @GetMapping("/api/stocks/product/{productId}")
    Result<List<Map<String, Object>>> getStockByProduct(@PathVariable("productId") Long productId);
    
    /**
     * 扣减商品库存（原子操作）
     * @param warehouseId 仓库ID
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 是否扣减成功
     */
    @PostMapping("/api/stocks/deduct")
    Result<Boolean> deductStock(
            @RequestParam("warehouseId") Long warehouseId,
            @RequestParam("productId") Long productId,
            @RequestParam("quantity") Integer quantity);

    /**
     * 获取仓库详情（含省市区地址和经纬度，用于物流路线创建）
     * @param id 仓库ID
     * @return 仓库信息
     */
    @GetMapping("/api/warehouses/{id}")
    Result<Map<String, Object>> getWarehouseById(@PathVariable("id") Long id);
}
