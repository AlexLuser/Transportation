package com.fm.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fm.shop.entity.TransferOrder;
import com.fm.shop.entity.TransferOrderItem;

import java.util.List;

public interface TransferOrderService extends IService<TransferOrder> {

    TransferOrder create(TransferOrder order, List<TransferOrderItem> items);

    TransferOrder approve(Long orderId);

    /**
     * 完成调拨（扣减调出仓库库存，增加调入仓库库存，联动生成出/入库单）
     */
    TransferOrder complete(Long orderId);

    boolean cancel(Long orderId, String reason);

    List<TransferOrderItem> getItems(Long orderId);

    List<TransferOrder> listByShop(Long shopId, String status);
}
