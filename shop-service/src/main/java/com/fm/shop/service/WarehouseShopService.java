package com.fm.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fm.shop.entity.WarehouseShop;

import java.util.List;
import java.util.Map;

public interface WarehouseShopService extends IService<WarehouseShop> {

    /** 查询商家有权访问的仓库列表（含仓库详情） */
    List<Map<String, Object>> getWarehousesByShopId(Long shopId);

    /** 查询仓库关联的所有商家 */
    List<Map<String, Object>> getShopsByWarehouseId(Long warehouseId);

    /** 商家是否有该仓库权限 */
    boolean hasPermission(Long shopId, Long warehouseId);

    /** 为仓库绑定商家 */
    WarehouseShop bindShop(Long warehouseId, Long shopId, String role);

    /** 解绑商家-仓库关联 */
    boolean unbindShop(Long warehouseId, Long shopId);
}
