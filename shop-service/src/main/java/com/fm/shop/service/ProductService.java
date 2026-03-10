package com.fm.shop.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fm.common.dto.PageResult;
import com.fm.shop.entity.Product;
import java.util.List;

/**
 * 商品服务接口
 * 提供商品信息的业务操作
 */
public interface ProductService {
    /**
     * 根据商品ID获取商品信息
     * @param productId 商品ID
     * @return 商品信息
     */
    Product getProductById(Long productId);
    
    /**
     * 根据商户ID获取商品列表
     * @param shopId 商户ID
     * @return 商品列表
     */
    List<Product> getProductsByShopId(Long shopId);
    
    /**
     * 搜索商品
     * @param keyword 关键词（商品名称、描述、编码）
     * @param categoryId 分类ID（可选）
     * @param shopId 商户ID（可选）
     * @return 商品列表
     */
    List<Product> searchProducts(String keyword, Long categoryId, Long shopId);
    
    /**
     * 保存或更新商品信息
     * @param product 商品信息
     * @return 保存后的商品信息
     */
    Product saveOrUpdateProduct(Product product);
    
    /**
     * 删除商品
     * @param productId 商品ID
     * @return 是否删除成功
     */
    boolean deleteProduct(Long productId);
    
    /**
     * 更新商品状态
     * @param productId 商品ID
     * @param status 状态：0=下架，1=上架，2=待审核
     * @return 是否更新成功
     */
    boolean updateProductStatus(Long productId, Integer status);
    
    /**
     * 获取所有上架商品列表（用于商城展示）
     * @param categoryId 分类ID（可选，用于筛选）
     * @param keyword 关键词（可选，用于搜索商品名称）
     * @return 上架商品列表
     */
    List<Product> getAllOnSaleProducts(Long categoryId, String keyword);
    
    /**
     * 分页获取所有上架商品列表（用于商城展示）
     * @param current 当前页码（从1开始）
     * @param size 每页大小
     * @param categoryId 分类ID（可选，用于筛选）
     * @param keyword 关键词（可选，用于搜索商品名称）
     * @param sortField 排序字段（可选：price-价格, salesCount-销量, createTime-创建时间，默认为createTime）
     * @param sortOrder 排序方向（可选：asc-升序, desc-降序，默认为desc）
     * @return 分页结果
     */
    PageResult<Product> getAllOnSaleProductsPage(Long current, Long size, Long categoryId, String keyword, String sortField, String sortOrder);
    
    /**
     * 分页获取商户商品列表
     * @param current 当前页码（从1开始）
     * @param size 每页大小
     * @param shopId 商户ID
     * @param categoryId 分类ID（可选）
     * @param keyword 关键词（可选）
     * @param sortField 排序字段（可选：price-价格, salesCount-销量, createTime-创建时间，默认为createTime）
     * @param sortOrder 排序方向（可选：asc-升序, desc-降序，默认为desc）
     * @return 分页结果
     */
    PageResult<Product> getProductsByShopIdPage(Long current, Long size, Long shopId, Long categoryId, String keyword, String sortField, String sortOrder);
    
    /**
     * 分页搜索商品
     * @param current 当前页码（从1开始）
     * @param size 每页大小
     * @param keyword 关键词（商品名称、描述、编码）
     * @param categoryId 分类ID（可选）
     * @param shopId 商户ID（可选）
     * @param sortField 排序字段（可选：price-价格, salesCount-销量, createTime-创建时间，默认为createTime）
     * @param sortOrder 排序方向（可选：asc-升序, desc-降序，默认为desc）
     * @return 分页结果
     */
    PageResult<Product> searchProductsPage(Long current, Long size, String keyword, Long categoryId, Long shopId, String sortField, String sortOrder);
}

