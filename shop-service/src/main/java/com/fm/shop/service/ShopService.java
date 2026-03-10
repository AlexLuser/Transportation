package com.fm.shop.service;

import com.fm.shop.entity.Shop;
import java.util.List;

/**
 * 商户服务接口
 * 提供商户信息的业务操作
 */
public interface ShopService {
    /**
     * 根据用户ID获取商户信息
     * @param userId 用户ID
     * @return 商户信息
     */
    Shop getShopByUserId(Long userId);
    
    /**
     * 根据商户ID获取商户信息
     * @param shopId 商户ID
     * @return 商户信息
     */
    Shop getShopById(Long shopId);
    
    /**
     * 保存或更新商户信息
     * @param shop 商户信息
     * @return 保存后的商户信息
     */
    Shop saveOrUpdateShop(Shop shop);
    
    /**
     * 获取所有商户列表（管理员使用）
     * @return 商户列表
     */
    List<Shop> getAllShops();
    
    /**
     * 更新商户状态
     * @param shopId 商户ID
     * @param status 状态：0=禁用，1=启用，2=待审核
     * @return 是否更新成功
     */
    boolean updateShopStatus(Long shopId, Integer status);
    
    /**
     * 删除商户信息
     * @param shopId 商户ID
     * @return 是否删除成功
     */
    boolean deleteShop(Long shopId);
}

