package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.shop.entity.*;
import com.fm.shop.mapper.*;
import com.fm.shop.service.StockService;
import com.fm.shop.service.TransferOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Random;

@Service
public class TransferOrderServiceImpl extends ServiceImpl<TransferOrderMapper, TransferOrder>
        implements TransferOrderService {

    @Autowired
    private TransferOrderMapper transferOrderMapper;
    @Autowired
    private TransferOrderItemMapper itemMapper;
    @Autowired
    private StockService stockService;
    @Autowired
    private InboundOrderMapper inboundOrderMapper;
    @Autowired
    private InboundOrderItemMapper inboundOrderItemMapper;
    @Autowired
    private OutboundOrderMapper outboundOrderMapper;
    @Autowired
    private OutboundOrderItemMapper outboundOrderItemMapper;

    @Override
    @Transactional
    public TransferOrder create(TransferOrder order, List<TransferOrderItem> items) {
        if (order.getSrcWarehouseId() != null
                && order.getSrcWarehouseId().equals(order.getDstWarehouseId())) {
            throw new RuntimeException("调出仓库和调入仓库不能相同");
        }
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("调拨明细不能为空");
        }
        order.setOrderNo(generateOrderNo("TR"));
        transferOrderMapper.insert(order);
        for (TransferOrderItem item : items) {
            item.setTransferOrderId(order.getId());
            item.setStatus("PENDING");
            itemMapper.insert(item);
        }
        return order;
    }

    @Override
    public TransferOrder approve(Long orderId) {
        LambdaUpdateWrapper<TransferOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TransferOrder::getId, orderId)
                .eq(TransferOrder::getStatus, "PENDING")
                .set(TransferOrder::getStatus, "APPROVED");
        transferOrderMapper.update(null, wrapper);
        return transferOrderMapper.selectById(orderId);
    }

    @Override
    @Transactional
    public TransferOrder complete(Long orderId) {
        TransferOrder order = transferOrderMapper.selectById(orderId);
        if (order == null) return null;

        List<TransferOrderItem> items = getItems(orderId);
        Date now = new Date();

        // ── 1. 库存转移 ──
        for (TransferOrderItem item : items) {
            boolean ok = stockService.deductStock(order.getSrcWarehouseId(), item.getProductId(), item.getQuantity());
            if (!ok) {
                throw new RuntimeException("调出仓库库存不足：商品 [" + item.getProductName() + "]，需要 "
                        + item.getQuantity() + " 件，仓库实际库存不足或该商品不存在");
            }
            stockService.addStock(order.getDstWarehouseId(), item.getProductId(), item.getQuantity());
            item.setStatus("DONE");
            itemMapper.updateById(item);
        }

        // ── 2. 自动生成调出仓的出库单（DONE） ──
        OutboundOrder outbound = new OutboundOrder();
        outbound.setOrderNo(generateOrderNo("OB-TR"));
        outbound.setWarehouseId(order.getSrcWarehouseId());
        outbound.setShopId(order.getShopId());
        outbound.setDestType("TRANSFER");
        outbound.setRelatedId(orderId);
        outbound.setStatus("DONE");
        outbound.setActualTime(now);
        outbound.setRemark("调拨自动生成出库单，调拨单：" + order.getOrderNo());
        outboundOrderMapper.insert(outbound);
        for (TransferOrderItem item : items) {
            OutboundOrderItem oi = new OutboundOrderItem();
            oi.setOutboundOrderId(outbound.getId());
            oi.setProductId(item.getProductId());
            oi.setProductName(item.getProductName());
            oi.setQuantity(item.getQuantity());
            oi.setStatus("DONE");
            outboundOrderItemMapper.insert(oi);
        }

        // ── 3. 自动生成调入仓的入库单（DONE） ──
        InboundOrder inbound = new InboundOrder();
        inbound.setOrderNo(generateOrderNo("IB-TR"));
        inbound.setWarehouseId(order.getDstWarehouseId());
        inbound.setShopId(order.getShopId());
        inbound.setSourceType("TRANSFER");
        inbound.setRelatedId(orderId);
        inbound.setStatus("DONE");
        inbound.setActualTime(now);
        inbound.setRemark("调拨自动生成入库单，调拨单：" + order.getOrderNo());
        inboundOrderMapper.insert(inbound);
        for (TransferOrderItem item : items) {
            InboundOrderItem ii = new InboundOrderItem();
            ii.setInboundOrderId(inbound.getId());
            ii.setProductId(item.getProductId());
            ii.setProductName(item.getProductName());
            ii.setExpectedQty(item.getQuantity());
            ii.setActualQty(item.getQuantity());
            ii.setStatus("DONE");
            inboundOrderItemMapper.insert(ii);
        }

        order.setStatus("DONE");
        order.setActualTime(now);
        transferOrderMapper.updateById(order);
        return order;
    }

    @Override
    public boolean cancel(Long orderId, String reason) {
        LambdaUpdateWrapper<TransferOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TransferOrder::getId, orderId)
                .in(TransferOrder::getStatus, "PENDING", "APPROVED")
                .set(TransferOrder::getStatus, "CANCELLED")
                .set(TransferOrder::getRemark, reason);
        return transferOrderMapper.update(null, wrapper) > 0;
    }

    @Override
    public List<TransferOrderItem> getItems(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getTransferOrderId, orderId));
    }

    @Override
    public List<TransferOrder> listByShop(Long shopId, String status) {
        LambdaQueryWrapper<TransferOrder> wrapper = new LambdaQueryWrapper<>();
        if (shopId != null) wrapper.eq(TransferOrder::getShopId, shopId);
        if (status != null && !status.isEmpty()) wrapper.eq(TransferOrder::getStatus, status);
        wrapper.orderByDesc(TransferOrder::getCreateTime);
        return transferOrderMapper.selectList(wrapper);
    }

    private String generateOrderNo(String prefix) {
        return prefix + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }
}
