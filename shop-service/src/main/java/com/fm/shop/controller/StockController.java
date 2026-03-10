package com.fm.shop.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.shop.entity.Product;
import com.fm.shop.entity.Shop;
import com.fm.shop.entity.WarehouseProduct;
import com.fm.shop.service.ProductService;
import com.fm.shop.service.ShopService;
import com.fm.shop.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 库存管理Controller
 * 提供商品库存的RESTful API接口
 * 接口设计：
 * GET    /api/stocks/product/{productId}    - 获取商品库存（所有仓库）
 * GET    /api/stocks/warehouse/{warehouseId} - 获取仓库库存（所有商品）
 * PUT    /api/stocks                         - 修改商品库存
 */
@Tag(name = "库存管理", description = "商品库存相关接口")
@RestController
@RequestMapping("/api/stocks")
public class StockController {
    
    @Autowired
    private StockService stockService;
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private ShopService shopService;
    
    /**
     * 获取商品库存
     * @param productId 商品ID
     * @return 该商品在所有仓库的库存列表
     */
    @Operation(summary = "获取商品库存", description = "根据商品ID获取该商品在所有仓库的库存")
    @GetMapping("/product/{productId}")
    public Result<List<WarehouseProduct>> getStockByProduct(
            @Parameter(description = "商品ID", required = true)
            @PathVariable Long productId) {
        List<WarehouseProduct> stocks = stockService.getStockByProductId(productId);
        return Result.success(stocks);
    }
    
    /**
     * 获取仓库库存
     * @param warehouseId 仓库ID
     * @return 该仓库所有商品的库存列表
     */
    @Operation(summary = "获取仓库库存", description = "根据仓库ID获取该仓库所有商品的库存")
    @GetMapping("/warehouse/{warehouseId}")
    public Result<List<WarehouseProduct>> getStockByWarehouse(
            @Parameter(description = "仓库ID", required = true)
            @PathVariable Long warehouseId) {
        List<WarehouseProduct> stocks = stockService.getStockByWarehouseId(warehouseId);
        return Result.success(stocks);
    }
    
    /**
     * 修改商品库存
     * @param userIdHeader 用户ID（从请求头获取）
     * @param warehouseProduct 库存信息（必须包含warehouseId、productId、stock）
     * @return 更新后的库存信息
     */
    @Operation(summary = "修改商品库存", description = "修改指定仓库中指定商品的库存数量")
    @PutMapping
    public Result<WarehouseProduct> updateStock(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody WarehouseProduct warehouseProduct) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        if (warehouseProduct.getWarehouseId() == null || warehouseProduct.getProductId() == null) {
            return Result.error("仓库ID和商品ID不能为空");
        }
        
        if (warehouseProduct.getStock() == null || warehouseProduct.getStock() < 0) {
            return Result.error("库存数量不能为负数");
        }
        
        // 验证商品是否属于当前商户
        Product product = productService.getProductById(warehouseProduct.getProductId());
        if (product == null) {
            return Result.error("商品不存在");
        }
        
        Shop shop = shopService.getShopByUserId(userId);
        if (shop == null || !product.getShopId().equals(shop.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        WarehouseProduct result = stockService.updateStock(
                warehouseProduct.getWarehouseId(),
                warehouseProduct.getProductId(),
                warehouseProduct.getStock()
        );
        return Result.success(result);
    }
    
    /**
     * 扣减商品库存（订单服务调用）
     * @param warehouseId 仓库ID
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 是否扣减成功
     */
    @Operation(summary = "扣减商品库存", description = "原子操作扣减指定仓库中指定商品的库存（订单服务调用）")
    @PostMapping("/deduct")
    public Result<Boolean> deductStock(
            @RequestParam("warehouseId") Long warehouseId,
            @RequestParam("productId") Long productId,
            @RequestParam("quantity") Integer quantity) {
        if (warehouseId == null || productId == null || quantity == null || quantity <= 0) {
            return Result.error("参数错误：仓库ID、商品ID和扣减数量不能为空，且扣减数量必须大于0");
        }
        boolean success = stockService.deductStock(warehouseId, productId, quantity);
        if (!success) {
            return Result.error("库存不足，扣减失败");
        }
        return Result.success(true);
    }
}

