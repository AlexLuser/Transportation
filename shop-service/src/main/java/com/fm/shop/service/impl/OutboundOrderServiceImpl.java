package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.shop.entity.OutboundOrder;
import com.fm.shop.entity.OutboundOrderItem;
import com.fm.shop.mapper.OutboundOrderItemMapper;
import com.fm.shop.mapper.OutboundOrderMapper;
import com.fm.shop.service.OutboundOrderService;
import com.fm.shop.service.StockService;
import com.fm.shop.service.WarehouseLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Random;

@Service
public class OutboundOrderServiceImpl extends ServiceImpl<OutboundOrderMapper, OutboundOrder>
        implements OutboundOrderService {

    @Autowired
    private OutboundOrderMapper outboundOrderMapper;
    @Autowired
    private OutboundOrderItemMapper itemMapper;
    @Autowired
    private StockService stockService;
    @Autowired
    private WarehouseLocationService locationService;

    @Override
    @Transactional
    public OutboundOrder create(OutboundOrder order, List<OutboundOrderItem> items) {
        order.setOrderNo(generateOrderNo("OB"));
        order.setStatus("PENDING");
        outboundOrderMapper.insert(order);
        for (OutboundOrderItem item : items) {
            item.setOutboundOrderId(order.getId());
            item.setStatus("PENDING");
            itemMapper.insert(item);
        }
        return order;
    }

    @Override
    public OutboundOrder startProcessing(Long orderId) {
        LambdaUpdateWrapper<OutboundOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(OutboundOrder::getId, orderId)
                .eq(OutboundOrder::getStatus, "PENDING")
                .set(OutboundOrder::getStatus, "PROCESSING");
        outboundOrderMapper.update(null, wrapper);
        return outboundOrderMapper.selectById(orderId);
    }

    @Override
    @Transactional
    public OutboundOrder complete(Long orderId) {
        OutboundOrder order = outboundOrderMapper.selectById(orderId);
        if (order == null) return null;

        List<OutboundOrderItem> items = getItems(orderId);
        for (OutboundOrderItem item : items) {
            // 原子扣减库存
            stockService.deductStock(order.getWarehouseId(), item.getProductId(), item.getQuantity());
            // 释放库位占用
            if (item.getLocationId() != null) {
                locationService.updateStock(item.getLocationId(), -item.getQuantity());
            }
            item.setStatus("DONE");
            itemMapper.updateById(item);
        }

        order.setStatus("DONE");
        order.setActualTime(new Date());
        outboundOrderMapper.updateById(order);
        return order;
    }

    @Override
    public boolean cancel(Long orderId, String reason) {
        LambdaUpdateWrapper<OutboundOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(OutboundOrder::getId, orderId)
                .in(OutboundOrder::getStatus, "PENDING", "PROCESSING")
                .set(OutboundOrder::getStatus, "CANCELLED")
                .set(OutboundOrder::getRemark, reason);
        return outboundOrderMapper.update(null, wrapper) > 0;
    }

    @Override
    public List<OutboundOrderItem> getItems(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundOrderId, orderId));
    }

    @Override
    public List<OutboundOrder> listByWarehouseAndShop(Long warehouseId, Long shopId, String status) {
        LambdaQueryWrapper<OutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (warehouseId != null) wrapper.eq(OutboundOrder::getWarehouseId, warehouseId);
        if (shopId != null) wrapper.eq(OutboundOrder::getShopId, shopId);
        if (status != null && !status.isEmpty()) wrapper.eq(OutboundOrder::getStatus, status);
        wrapper.orderByDesc(OutboundOrder::getCreateTime);
        return outboundOrderMapper.selectList(wrapper);
    }

    private String generateOrderNo(String prefix) {
        return prefix + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }
}
