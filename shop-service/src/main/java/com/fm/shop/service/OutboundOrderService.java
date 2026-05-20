package com.fm.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fm.shop.entity.OutboundOrder;
import com.fm.shop.entity.OutboundOrderItem;

import java.util.List;

public interface OutboundOrderService extends IService<OutboundOrder> {

    OutboundOrder create(OutboundOrder order, List<OutboundOrderItem> items);

    OutboundOrder startProcessing(Long orderId);

    /**
     * 完成出库（扣减库存，释放库位占用）
     */
    OutboundOrder complete(Long orderId);

    boolean cancel(Long orderId, String reason);

    List<OutboundOrderItem> getItems(Long orderId);

    List<OutboundOrder> listByWarehouseAndShop(Long warehouseId, Long shopId, String status);
}
