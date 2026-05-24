package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.shop.entity.InventoryCheck;
import com.fm.shop.entity.InventoryCheckItem;
import com.fm.shop.entity.Product;
import com.fm.shop.entity.WarehouseProduct;
import com.fm.shop.mapper.InventoryCheckItemMapper;
import com.fm.shop.mapper.InventoryCheckMapper;
import com.fm.shop.service.InventoryCheckService;
import com.fm.shop.service.ProductService;
import com.fm.shop.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

@Service
public class InventoryCheckServiceImpl extends ServiceImpl<InventoryCheckMapper, InventoryCheck>
        implements InventoryCheckService {

    @Autowired
    private InventoryCheckMapper checkMapper;
    @Autowired
    private InventoryCheckItemMapper itemMapper;
    @Autowired
    private StockService stockService;
    @Autowired
    private ProductService productService;

    /**
     * 按仓库账面库存生成盘点明细（FULL/ZONE/DYNAMIC 均以此为准；分区盘时 zoneCode 仅作业务备注，仓内库存未区分到库位）。
     */
    @Override
    @Transactional
    public InventoryCheck create(InventoryCheck check) {
        check.setCheckNo(generateCheckNo());
        check.setStatus("PENDING");
        checkMapper.insert(check);

        List<WarehouseProduct> stocks = stockService.getStockByWarehouseId(check.getWarehouseId());
        for (WarehouseProduct wp : stocks) {
            Product p = wp.getProductId() != null ? productService.getProductById(wp.getProductId()) : null;
            Long effectiveShopId = wp.getShopId() != null ? wp.getShopId() : (p != null ? p.getShopId() : null);
            if (check.getShopId() != null && !check.getShopId().equals(effectiveShopId)) {
                continue;
            }
            InventoryCheckItem item = new InventoryCheckItem();
            item.setCheckId(check.getId());
            item.setLocationId(null);
            item.setProductId(wp.getProductId());
            item.setShopId(effectiveShopId);
            item.setProductName(p != null && p.getProductName() != null
                    ? p.getProductName()
                    : ("商品#" + wp.getProductId()));
            item.setSystemQty(wp.getStock() != null ? wp.getStock() : 0);
            item.setStatus("PENDING");
            itemMapper.insert(item);
        }

        return check;
    }

    @Override
    public InventoryCheck startCheck(Long checkId) {
        LambdaUpdateWrapper<InventoryCheck> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(InventoryCheck::getId, checkId)
                .eq(InventoryCheck::getStatus, "PENDING")
                .set(InventoryCheck::getStatus, "PROCESSING")
                .set(InventoryCheck::getStartTime, new Date());
        checkMapper.update(null, wrapper);
        return checkMapper.selectById(checkId);
    }

    @Override
    public InventoryCheckItem submitItemResult(Long checkId, Long itemId, Integer actualQty) {
        if (actualQty == null || actualQty < 0) {
            throw new BusinessException(ResultCode.FAIL, "实盘数量无效");
        }
        InventoryCheckItem item = itemMapper.selectById(itemId);
        if (item == null || !item.getCheckId().equals(checkId)) {
            return null;
        }
        item.setActualQty(actualQty);
        item.setStatus(Objects.equals(actualQty, item.getSystemQty()) ? "COUNTED" : "DIFF");
        itemMapper.updateById(item);

        InventoryCheck check = checkMapper.selectById(checkId);
        if (check != null && "PROCESSING".equals(check.getStatus())) {
            List<InventoryCheckItem> all = getItems(checkId);
            boolean allFilled = all.stream().allMatch(i -> i.getActualQty() != null);
            if (allFilled) {
                check.setStatus("CONFIRMING");
                checkMapper.updateById(check);
            }
        }
        return item;
    }

    @Override
    @Transactional
    public InventoryCheck confirmAndAdjust(Long checkId) {
        InventoryCheck check = checkMapper.selectById(checkId);
        if (check == null) {
            return null;
        }
        if ("DONE".equals(check.getStatus())) {
            return check;
        }
        if (!"CONFIRMING".equals(check.getStatus()) && !"PROCESSING".equals(check.getStatus())) {
            throw new BusinessException(ResultCode.FAIL, "当前盘点单状态不可确认");
        }

        List<InventoryCheckItem> items = getItems(checkId);
        for (InventoryCheckItem item : items) {
            if (item.getActualQty() == null) {
                continue;
            }
            if (!Objects.equals(item.getSystemQty(), item.getActualQty())) {
                stockService.updateStock(check.getWarehouseId(), item.getProductId(), item.getActualQty());
            }
        }

        check.setStatus("DONE");
        check.setEndTime(new Date());
        checkMapper.updateById(check);
        return check;
    }

    @Override
    public List<InventoryCheckItem> getItems(Long checkId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InventoryCheckItem>()
                .eq(InventoryCheckItem::getCheckId, checkId));
    }

    @Override
    public Map<String, Object> getCheckSummary(Long checkId) {
        return itemMapper.selectCheckSummary(checkId);
    }

    @Override
    public List<InventoryCheck> listByWarehouse(Long warehouseId, Long shopId, String status) {
        LambdaQueryWrapper<InventoryCheck> wrapper = new LambdaQueryWrapper<>();
        if (warehouseId != null) wrapper.eq(InventoryCheck::getWarehouseId, warehouseId);
        if (shopId != null) wrapper.eq(InventoryCheck::getShopId, shopId);
        if (status != null && !status.isEmpty()) wrapper.eq(InventoryCheck::getStatus, status);
        wrapper.orderByDesc(InventoryCheck::getCreateTime);
        return checkMapper.selectList(wrapper);
    }

    private String generateCheckNo() {
        return "CC" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }
}
