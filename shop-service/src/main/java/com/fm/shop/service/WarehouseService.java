package com.fm.shop.service;

import com.fm.shop.entity.Warehouse;
import java.util.List;

/**
 * 仓库服务接口
 * 提供仓库信息的业务操作
 */
public interface WarehouseService {
    /**
     * 获取所有仓库列表
     * @return 仓库列表
     */
    List<Warehouse> getAllWarehouses();
    
    /**
     * 根据仓库ID获取仓库信息
     * @param warehouseId 仓库ID
     * @return 仓库信息
     */
    Warehouse getWarehouseById(Long warehouseId);
    
    /**
     * 保存或更新仓库信息
     * @param warehouse 仓库信息
     * @return 保存后的仓库信息
     */
    Warehouse saveOrUpdateWarehouse(Warehouse warehouse);
    
    /**
     * 更新仓库容量
     * @param warehouseId 仓库ID
     * @param capacity 容量（单位：件/箱等，0表示无限制）
     * @return 是否更新成功
     */
    boolean updateWarehouseCapacity(Long warehouseId, Integer capacity);
    
    /**
     * 删除仓库信息
     * @param warehouseId 仓库ID
     * @return 是否删除成功
     */
    boolean deleteWarehouse(Long warehouseId);
}

