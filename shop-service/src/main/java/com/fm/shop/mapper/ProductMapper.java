package com.fm.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.shop.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品信息Mapper接口
 * 提供商品信息的数据库操作
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}

