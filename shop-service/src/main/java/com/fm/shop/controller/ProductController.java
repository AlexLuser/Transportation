package com.fm.shop.controller;

import com.fm.common.dto.PageResult;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.shop.entity.Product;
import com.fm.shop.entity.Shop;
import com.fm.shop.service.ProductService;
import com.fm.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 商品管理Controller
 * 提供商品信息的RESTful API接口
 * 接口设计：
 * GET    /api/products/{id}      - 获取商品详情
 * GET    /api/products           - 获取商品列表
 * GET    /api/products/search   - 搜索商品
 * POST   /api/products           - 添加商品
 * PUT    /api/products           - 修改商品
 * DELETE /api/products/{id}      - 删除商品
 */
@Tag(name = "商品管理", description = "商品信息相关接口")
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private ShopService shopService;
    
    /**
     * 获取商品详情
     * @param id 商品ID
     * @return 商品信息
     */
    @Operation(summary = "获取商品详情", description = "根据商品ID获取商品详情")
    @GetMapping("/{id}")
    public Result<Product> getProduct(
            @Parameter(description = "商品ID", required = true)
            @PathVariable Long id) {
        Product product = productService.getProductById(id);
        if (product == null) {
            return Result.error("商品不存在");
        }
        return Result.success(product);
    }
    
    /**
     * 获取商品列表（分页）
     * @param userIdHeader 用户ID（从请求头获取，可选）
     * @param shopId 商户ID（查询参数，可选。如果未提供且用户已登录，则获取当前用户的商户商品）
     * @param current 当前页码（从1开始，默认1）
     * @param size 每页大小（默认10）
     * @param categoryId 分类ID（可选）
     * @param keyword 关键词（可选）
     * @param sortField 排序字段（可选：price-价格, salesCount-销量, createTime-创建时间，默认为createTime）
     * @param sortOrder 排序方向（可选：asc-升序, desc-降序，默认为desc）
     * @return 分页商品列表
     */
    @Operation(summary = "获取商品列表（分页）", description = "根据商户ID获取商品列表，支持分页、排序、分类筛选和关键词搜索")
    @GetMapping
    public Result<PageResult<Product>> getProducts(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestParam(value = "shopId", required = false) Long shopId,
            @Parameter(description = "当前页码（从1开始）")
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @Parameter(description = "每页大小")
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @Parameter(description = "分类ID（可选）")
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(description = "关键词（可选）")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "排序字段（可选：price-价格, salesCount-销量, createTime-创建时间）")
            @RequestParam(value = "sortField", required = false) String sortField,
            @Parameter(description = "排序方向（可选：asc-升序, desc-降序）")
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        // 如果未指定shopId，且用户已登录，则获取当前用户的商户商品
        if (shopId == null && StringUtils.hasText(userIdHeader)) {
            Long userId = Long.parseLong(userIdHeader);
            Shop shop = shopService.getShopByUserId(userId);
            if (shop != null) {
                shopId = shop.getId();
            }
        }
        
        PageResult<Product> pageResult;
        if (shopId != null) {
            pageResult = productService.getProductsByShopIdPage(
                current, size, shopId, categoryId, keyword, sortField, sortOrder);
        } else {
            // 如果没有shopId，返回空分页结果
            pageResult = PageResult.empty(current, size);
        }
        return Result.success(pageResult);
    }
    
    /**
     * 搜索商品（分页）
     * @param keyword 关键词（商品名称、描述、编码）
     * @param categoryId 分类ID（可选）
     * @param shopId 商户ID（可选）
     * @param current 当前页码（从1开始，默认1）
     * @param size 每页大小（默认10）
     * @param sortField 排序字段（可选：price-价格, salesCount-销量, createTime-创建时间，默认为createTime）
     * @param sortOrder 排序方向（可选：asc-升序, desc-降序，默认为desc）
     * @return 分页商品列表
     */
    @Operation(summary = "搜索商品（分页）", description = "根据关键词、分类、商户搜索商品，支持分页和排序")
    @GetMapping("/search")
    public Result<PageResult<Product>> searchProducts(
            @Parameter(description = "关键词（商品名称、描述、编码）")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "分类ID（可选）")
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(description = "商户ID（可选）")
            @RequestParam(value = "shopId", required = false) Long shopId,
            @Parameter(description = "当前页码（从1开始）")
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @Parameter(description = "每页大小")
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @Parameter(description = "排序字段（可选：price-价格, salesCount-销量, createTime-创建时间）")
            @RequestParam(value = "sortField", required = false) String sortField,
            @Parameter(description = "排序方向（可选：asc-升序, desc-降序）")
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        PageResult<Product> pageResult = productService.searchProductsPage(
            current, size, keyword, categoryId, shopId, sortField, sortOrder);
        return Result.success(pageResult);
    }
    
    /**
     * 添加商品
     * @param userIdHeader 用户ID（从请求头获取）
     * @param product 商品信息
     * @return 添加后的商品信息
     */
    @Operation(summary = "添加商品", description = "添加新的商品")
    @PostMapping
    public Result<Product> addProduct(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Product product) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Shop shop = shopService.getShopByUserId(userId);
        if (shop == null) {
            return Result.error("商户信息不存在，请先完善商户信息");
        }
        
        product.setShopId(shop.getId());
        product = productService.saveOrUpdateProduct(product);
        return Result.success(product);
    }
    
    /**
     * 修改商品
     * @param userIdHeader 用户ID（从请求头获取）
     * @param product 商品信息（必须包含id）
     * @return 修改后的商品信息
     */
    @Operation(summary = "修改商品", description = "修改商品信息")
    @PutMapping
    public Result<Product> updateProduct(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Product product) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        if (product.getId() == null) {
            return Result.error("商品ID不能为空");
        }
        
        Product existingProduct = productService.getProductById(product.getId());
        if (existingProduct == null) {
            return Result.error("商品不存在");
        }
        
        Shop shop = shopService.getShopByUserId(userId);
        if (shop == null || !existingProduct.getShopId().equals(shop.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        product.setShopId(shop.getId());
        product = productService.saveOrUpdateProduct(product);
        return Result.success(product);
    }
    
    /**
     * 删除商品
     * @param userIdHeader 用户ID（从请求头获取）
     * @param id 商品ID
     * @return 是否删除成功
     */
    @Operation(summary = "删除商品", description = "根据商品ID删除商品")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteProduct(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "商品ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Product product = productService.getProductById(id);
        if (product == null) {
            return Result.error("商品不存在");
        }
        
        Shop shop = shopService.getShopByUserId(userId);
        if (shop == null || !product.getShopId().equals(shop.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        boolean success = productService.deleteProduct(id);
        return Result.success(success);
    }
}

