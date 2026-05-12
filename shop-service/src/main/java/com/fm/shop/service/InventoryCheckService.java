package com.fm.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fm.shop.entity.InventoryCheck;
import com.fm.shop.entity.InventoryCheckItem;

import java.util.List;
import java.util.Map;

public interface InventoryCheckService extends IService<InventoryCheck> {

    /** 发起盘点（自动生成盘点明细条目） */
    InventoryCheck create(InventoryCheck check);

    /** 开始盘点 */
    InventoryCheck startCheck(Long checkId);

    /** 录入单条盘点结果 */
    InventoryCheckItem submitItemResult(Long checkId, Long itemId, Integer actualQty);

    /**
     * 确认盘点完成（CONFIRMING → DONE）
     * 对有差异的条目同步调整库存
     */
    InventoryCheck confirmAndAdjust(Long checkId);

    List<InventoryCheckItem> getItems(Long checkId);

    Map<String, Object> getCheckSummary(Long checkId);

    List<InventoryCheck> listByWarehouse(Long warehouseId, Long shopId, String status);
}
