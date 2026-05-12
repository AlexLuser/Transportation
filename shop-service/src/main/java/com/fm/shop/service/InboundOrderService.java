package com.fm.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fm.shop.entity.InboundOrder;
import com.fm.shop.entity.InboundOrderItem;

import java.util.List;

public interface InboundOrderService extends IService<InboundOrder> {

    /** 创建入库单（含明细） */
    InboundOrder create(InboundOrder order, List<InboundOrderItem> items);

    /** 开始入库（PENDING → PROCESSING） */
    InboundOrder startProcessing(Long orderId);

    /**
     * 完成入库（PROCESSING → DONE）
     * 自动更新库存数量和库位占用量
     */
    InboundOrder complete(Long orderId, List<InboundOrderItem> actualItems);

    /** 取消入库单 */
    boolean cancel(Long orderId, String reason);

    /** 查询明细 */
    List<InboundOrderItem> getItems(Long orderId);

    /** 按仓库和商家分页查询 */
    List<InboundOrder> listByWarehouseAndShop(Long warehouseId, Long shopId, String status);
}
