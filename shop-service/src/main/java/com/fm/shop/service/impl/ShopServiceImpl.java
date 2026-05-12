package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.shop.entity.Shop;
import com.fm.shop.mapper.ShopMapper;
import com.fm.shop.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 商户服务实现类
 * 提供商户信息的业务逻辑实现
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {
    @Autowired
    private ShopMapper shopMapper;

    @Override
    @Cacheable(value = "shopByUser", key = "#userId", unless = "#result == null")
    public Shop getShopByUserId(Long userId) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shop::getUserId, userId);
        return shopMapper.selectOne(wrapper);
    }

    @Override
    @Cacheable(value = "shopById", key = "#shopId", unless = "#result == null")
    public Shop getShopById(Long shopId) {
        return shopMapper.selectById(shopId);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "shopById",   key = "#shop.id",     condition = "#shop.id != null"),
        @CacheEvict(value = "shopByUser", key = "#shop.userId", condition = "#shop.userId != null")
    })
    public Shop saveOrUpdateShop(Shop shop) {
        if (shop.getId() == null) {
            shopMapper.insert(shop);
        } else {
            shopMapper.updateById(shop);
        }
        return shop;
    }

    @Override
    public List<Shop> getAllShops() {
        return shopMapper.selectList(null);
    }

    @Override
    @CacheEvict(value = "shopById", key = "#shopId")
    public boolean updateShopStatus(Long shopId, Integer status) {
        LambdaUpdateWrapper<Shop> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Shop::getId, shopId).set(Shop::getStatus, status);
        return shopMapper.update(null, wrapper) > 0;
    }

    @Override
    @CacheEvict(value = "shopById", key = "#shopId")
    public boolean deleteShop(Long shopId) {
        return shopMapper.deleteById(shopId) > 0;
    }
}

