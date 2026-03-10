package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.shop.entity.Shop;
import com.fm.shop.mapper.ShopMapper;
import com.fm.shop.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public Shop getShopByUserId(Long userId) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shop::getUserId, userId);
        return shopMapper.selectOne(wrapper);
    }

    @Override
    public Shop getShopById(Long shopId) {
        return shopMapper.selectById(shopId);
    }

    @Override
    public Shop saveOrUpdateShop(Shop shop) {
        if (shop.getId() == null) {
            // 新增商户
            shopMapper.insert(shop);
        } else {
            // 更新商户
            shopMapper.updateById(shop);
        }
        return shop;
    }

    @Override
    public List<Shop> getAllShops() {
        return shopMapper.selectList(null);
    }

    @Override
    public boolean updateShopStatus(Long shopId, Integer status) {
        LambdaUpdateWrapper<Shop> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Shop::getId, shopId).set(Shop::getStatus, status);
        return shopMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean deleteShop(Long shopId) {
        // 注意：删除商户前，应该先删除关联的商品信息
        // 这里只删除商户信息，商品的级联删除需要在业务层处理
        return shopMapper.deleteById(shopId) > 0;
    }
}

