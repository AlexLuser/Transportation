package com.fm.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fm.shop.entity.InventoryCheck;
import com.fm.shop.entity.InventoryCheckItem;
import com.fm.shop.entity.WarehouseLocation;
import com.fm.shop.entity.WarehouseProduct;
import com.fm.shop.mapper.InventoryCheckItemMapper;
import com.fm.shop.mapper.InventoryCheckMapper;
import com.fm.shop.service.InventoryCheckService;
import com.fm.shop.service.StockService;
import com.fm.shop.service.WarehouseLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class InventoryCheckServiceImpl extends ServiceImpl<InventoryCheckMapper, InventoryCheck>
        implements InventoryCheckService {

    @Autowired
    private InventoryCheckMapper checkMapper;
    @Autowired
    private InventoryCheckItemMapper itemMapper;
    @Autowired
    private WarehouseLocationService locationService;
    @Autowired
    private StockService stockService;

    @Override
    @Transactional
    public InventoryCheck create(InventoryCheck check) {
        check.setCheckNo(generateCheckNo());
        check.setStatus("PENDING");
        checkMapper.insert(check);

        // 自动生成盘点明细：按库位和库存生成条目
        List<WarehouseLocation> locations;
        if (check.getZoneCode() != null) {
            locations = locationService.getByZone(check.getWarehouseId(), check.getZoneCode());
        } else {
            locations = locationService.getByWarehouseId(check.getWarehouseId());
        }

        for (WarehouseLocation loc : locations) {
            List<WarehouseProduct> stocks = stockService.getStockByWarehouseId(check.getWarehouseId());
            for (WarehouseProduct wp : stocks) {
                if (check.getShopId() != null && !check.getShopId().equals(wp.getShopId())) {
                    continue;
                }
                InventoryCheckItem item = new InventoryCheckItem();
                item.setCheckId(check.getId());
                item.setLocationId(loc.getId());
                item.setProductId(wp.getProductId());
                item.setShopId(wp.getShopId());
                item.setProductName("商品#" + wp.getProductId());
                item.setSystemQty(wp.getStock() != null ? wp.getStock() : 0);
                item.setStatus("PENDING");
                itemMapper.insert(item);
                break; // 每个库位对应一条主要库存明细（简化实现）
            }
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
        InventoryCheckItem item = itemMapper.selectById(itemId);
        if (item == null || !item.getCheckId().equals(checkId)) return null;
        item.setActualQty(actualQty);
        item.setStatus(actualQty.equals(item.getSystemQty()) ? "COUNTED" : "DIFF");
        itemMapper.updateById(item);
        return item;
    }

    @Override
    @Transactional
    public InventoryCheck confirmAndAdjust(Long checkId) {
        InventoryCheck check = checkMapper.selectById(checkId);
        if (check == null) return null;

        List<InventoryCheckItem> items = getItems(checkId);
        for (InventoryCheckItem item : items) {
            if ("DIFF".equals(item.getStatus()) && item.getActualQty() != null) {
                // 以实盘数量为准，强制覆盖库存
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
