package com.fm.shop.controller;

import com.fm.common.dto.PageResult;
import com.fm.common.result.Result;
import com.fm.shop.entity.Product;
import com.fm.shop.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商城Controller
 * 提供面向C端用户的商城浏览接口
 * 接口设计：
 * GET    /api/mall/products      - 获取商城商品列表（所有上架商品）
 */
@Tag(name = "商城", description = "商城商品浏览相关接口")
@RestController
@RequestMapping("/api/mall")
public class MallController {
    
    @Autowired
    private ProductService productService;
    
    /**
     * 获取商城商品列表（分页）
     * 返回所有上架的商品，支持按分类筛选、关键词搜索、分页和排序
     * 需要用户登录（通过网关JWT验证）
     * 
     * @param current 当前页码（从1开始，默认1）
     * @param size 每页大小（默认10）
     * @param categoryId 分类ID（可选，用于筛选特定分类的商品）
     * @param keyword 关键词（可选，用于搜索商品名称）
     * @param sortField 排序字段（可选：price-价格, salesCount-销量, createTime-创建时间，默认为createTime）
     * @param sortOrder 排序方向（可选：asc-升序, desc-降序，默认为desc）
     * @return 分页商品列表
     */
    @Operation(summary = "获取商城商品列表（分页）", description = "获取所有上架的商品列表，支持分页、排序、分类筛选和关键词搜索")
    @GetMapping("/products")
    public Result<PageResult<Product>> getMallProducts(
            @Parameter(description = "当前页码（从1开始）")
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @Parameter(description = "每页大小")
            @RequestParam(value = "size", defaultValue = "10") Long size,
            @Parameter(description = "分类ID（可选）")
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Parameter(description = "关键词（可选，用于搜索商品名称）")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "排序字段（可选：price-价格, salesCount-销量, createTime-创建时间）")
            @RequestParam(value = "sortField", required = false) String sortField,
            @Parameter(description = "排序方向（可选：asc-升序, desc-降序）")
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        PageResult<Product> pageResult = productService.getAllOnSaleProductsPage(
            current, size, categoryId, keyword, sortField, sortOrder);
        return Result.success(pageResult);
    }
}

