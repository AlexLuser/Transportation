package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.common.dto.PageResult;
import com.fm.shop.entity.Product;
import com.fm.shop.mapper.ProductMapper;
import com.fm.shop.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;

/**
 * 商品服务实现类
 * 提供商品信息的业务逻辑实现
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
    @Autowired
    private ProductMapper productMapper;

    @Override
    public Product getProductById(Long productId) {
        return productMapper.selectById(productId);
    }

    @Override
    public List<Product> getProductsByShopId(Long shopId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getShopId, shopId);
        return productMapper.selectList(wrapper);
    }

    @Override
    public List<Product> searchProducts(String keyword, Long categoryId, Long shopId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 关键词搜索：商品名称、描述、编码
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Product::getProductName, keyword)
                    .or().like(Product::getDescription, keyword)
                    .or().like(Product::getProductCode, keyword));
        }
        // 分类筛选
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        // 商户筛选
        if (shopId != null) {
            wrapper.eq(Product::getShopId, shopId);
        }
        return productMapper.selectList(wrapper);
    }

    @Override
    public Product saveOrUpdateProduct(Product product) {
        if (product.getId() == null) {
            // 新增商品
            productMapper.insert(product);
        } else {
            // 更新商品
            productMapper.updateById(product);
        }
        return product;
    }

    @Override
    public boolean deleteProduct(Long productId) {
        // 注意：删除商品前，应该先删除关联的库存信息
        // 这里只删除商品信息，库存的级联删除需要在业务层处理
        return productMapper.deleteById(productId) > 0;
    }

    @Override
    public boolean updateProductStatus(Long productId, Integer status) {
        LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Product::getId, productId).set(Product::getStatus, status);
        return productMapper.update(null, wrapper) > 0;
    }

    @Override
    public List<Product> getAllOnSaleProducts(Long categoryId, String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 只查询上架的商品（status=1）
        wrapper.eq(Product::getStatus, 1);
        
        // 分类筛选
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        
        // 关键词搜索（商品名称）
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getProductName, keyword);
        }
        
        // 按创建时间倒序排列（最新商品在前）
        wrapper.orderByDesc(Product::getCreateTime);
        
        return productMapper.selectList(wrapper);
    }

    @Override
    public PageResult<Product> getAllOnSaleProductsPage(Long current, Long size, Long categoryId, String keyword, String sortField, String sortOrder) {
        // 创建分页对象
        Page<Product> page = new Page<>(current, size);
        
        // 构建查询条件
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 只查询上架的商品（status=1）
        wrapper.eq(Product::getStatus, 1);
        
        // 分类筛选
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        
        // 关键词搜索（商品名称）
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getProductName, keyword);
        }
        
        // 排序
        applySort(wrapper, sortField, sortOrder);
        
        // 执行分页查询
        IPage<Product> pageResult = productMapper.selectPage(page, wrapper);
        
        // 转换为PageResult
        return new PageResult<>(
            pageResult.getCurrent(),
            pageResult.getSize(),
            pageResult.getTotal(),
            pageResult.getRecords()
        );
    }

    @Override
    public PageResult<Product> getProductsByShopIdPage(Long current, Long size, Long shopId, Long categoryId, String keyword, String sortField, String sortOrder) {
        // 创建分页对象
        Page<Product> page = new Page<>(current, size);
        
        // 构建查询条件
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getShopId, shopId);
        
        // 分类筛选
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        
        // 关键词搜索
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Product::getProductName, keyword)
                    .or().like(Product::getDescription, keyword)
                    .or().like(Product::getProductCode, keyword));
        }
        
        // 排序
        applySort(wrapper, sortField, sortOrder);
        
        // 执行分页查询
        IPage<Product> pageResult = productMapper.selectPage(page, wrapper);
        
        // 转换为PageResult
        return new PageResult<>(
            pageResult.getCurrent(),
            pageResult.getSize(),
            pageResult.getTotal(),
            pageResult.getRecords()
        );
    }

    @Override
    public PageResult<Product> searchProductsPage(Long current, Long size, String keyword, Long categoryId, Long shopId, String sortField, String sortOrder) {
        // 创建分页对象
        Page<Product> page = new Page<>(current, size);
        
        // 构建查询条件
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        
        // 关键词搜索：商品名称、描述、编码
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Product::getProductName, keyword)
                    .or().like(Product::getDescription, keyword)
                    .or().like(Product::getProductCode, keyword));
        }
        
        // 分类筛选
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        
        // 商户筛选
        if (shopId != null) {
            wrapper.eq(Product::getShopId, shopId);
        }
        
        // 排序
        applySort(wrapper, sortField, sortOrder);
        
        // 执行分页查询
        IPage<Product> pageResult = productMapper.selectPage(page, wrapper);
        
        // 转换为PageResult
        return new PageResult<>(
            pageResult.getCurrent(),
            pageResult.getSize(),
            pageResult.getTotal(),
            pageResult.getRecords()
        );
    }
    
    /**
     * 应用排序规则
     * @param wrapper 查询条件包装器
     * @param sortField 排序字段（price-价格, salesCount-销量, createTime-创建时间）
     * @param sortOrder 排序方向（asc-升序, desc-降序）
     */
    private void applySort(LambdaQueryWrapper<Product> wrapper, String sortField, String sortOrder) {
        // 默认按创建时间倒序
        if (!StringUtils.hasText(sortField)) {
            sortField = "createTime";
        }
        if (!StringUtils.hasText(sortOrder)) {
            sortOrder = "desc";
        }
        
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        
        switch (sortField.toLowerCase()) {
            case "price":
                if (isAsc) {
                    wrapper.orderByAsc(Product::getPrice);
                } else {
                    wrapper.orderByDesc(Product::getPrice);
                }
                break;
            case "salescount":
            case "sales_count":
                if (isAsc) {
                    wrapper.orderByAsc(Product::getSalesCount);
                } else {
                    wrapper.orderByDesc(Product::getSalesCount);
                }
                break;
            case "createtime":
            case "create_time":
            default:
                if (isAsc) {
                    wrapper.orderByAsc(Product::getCreateTime);
                } else {
                    wrapper.orderByDesc(Product::getCreateTime);
                }
                break;
        }
    }
}

