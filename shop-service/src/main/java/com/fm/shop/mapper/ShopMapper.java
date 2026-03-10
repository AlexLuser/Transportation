package com.fm.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.shop.entity.Shop;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户信息Mapper接口
 * 提供商户信息的数据库操作
 */
@Mapper
public interface ShopMapper extends BaseMapper<Shop> {
}

