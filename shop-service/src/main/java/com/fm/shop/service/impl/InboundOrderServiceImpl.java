package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.shop.entity.InboundOrder;
import com.fm.shop.entity.InboundOrderItem;
import com.fm.shop.mapper.InboundOrderItemMapper;
import com.fm.shop.mapper.InboundOrderMapper;
import com.fm.shop.service.InboundOrderService;
import com.fm.shop.service.StockService;
import com.fm.shop.service.WarehouseLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Random;

@Service
public class InboundOrderServiceImpl extends ServiceImpl<InboundOrderMapper, InboundOrder>
        implements InboundOrderService {

    @Autowired
    private InboundOrderMapper inboundOrderMapper;
    @Autowired
    private InboundOrderItemMapper itemMapper;
    @Autowired
    private StockService stockService;
    @Autowired
    private WarehouseLocationService locationService;

    @Override
    @Transactional
    public InboundOrder create(InboundOrder order, List<InboundOrderItem> items) {
        order.setOrderNo(generateOrderNo("IB"));
        order.setStatus("PENDING");
        inboundOrderMapper.insert(order);
        for (InboundOrderItem item : items) {
            item.setInboundOrderId(order.getId());
            item.setStatus("PENDING");
            itemMapper.insert(item);
        }
        return order;
    }

    @Override
    public InboundOrder startProcessing(Long orderId) {
        LambdaUpdateWrapper<InboundOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(InboundOrder::getId, orderId)
                .eq(InboundOrder::getStatus, "PENDING")
                .set(InboundOrder::getStatus, "PROCESSING");
        inboundOrderMapper.update(null, wrapper);
        return inboundOrderMapper.selectById(orderId);
    }

    @Override
    @Transactional
    public InboundOrder complete(Long orderId, List<InboundOrderItem> actualItems) {
        InboundOrder order = inboundOrderMapper.selectById(orderId);
        if (order == null) return null;

        for (InboundOrderItem item : actualItems) {
            item.setInboundOrderId(orderId);
            item.setStatus("DONE");
            if (item.getId() != null) {
                itemMapper.updateById(item);
            } else {
                itemMapper.insert(item);
            }
            // 更新库存
            if (item.getActualQty() != null && item.getActualQty() > 0) {
                stockService.addStock(order.getWarehouseId(), item.getProductId(), item.getActualQty());
                // 更新库位占用
                if (item.getLocationId() != null) {
                    locationService.updateStock(item.getLocationId(), item.getActualQty());
                }
            }
        }

        order.setStatus("DONE");
        order.setActualTime(new Date());
        inboundOrderMapper.updateById(order);
        return order;
    }

    @Override
    public boolean cancel(Long orderId, String reason) {
        LambdaUpdateWrapper<InboundOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(InboundOrder::getId, orderId)
                .in(InboundOrder::getStatus, "PENDING", "PROCESSING")
                .set(InboundOrder::getStatus, "CANCELLED")
                .set(InboundOrder::getRemark, reason);
        return inboundOrderMapper.update(null, wrapper) > 0;
    }

    @Override
    public List<InboundOrderItem> getItems(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getInboundOrderId, orderId));
    }

    @Override
    public List<InboundOrder> listByWarehouseAndShop(Long warehouseId, Long shopId, String status) {
        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (warehouseId != null) wrapper.eq(InboundOrder::getWarehouseId, warehouseId);
        if (shopId != null) wrapper.eq(InboundOrder::getShopId, shopId);
        if (status != null && !status.isEmpty()) wrapper.eq(InboundOrder::getStatus, status);
        wrapper.orderByDesc(InboundOrder::getCreateTime);
        return inboundOrderMapper.selectList(wrapper);
    }

    private String generateOrderNo(String prefix) {
        return prefix + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }
}
