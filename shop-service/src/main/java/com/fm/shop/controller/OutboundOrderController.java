package com.fm.shop.controller;

import com.fm.common.result.Result;
import com.fm.shop.entity.OutboundOrder;
import com.fm.shop.entity.OutboundOrderItem;
import com.fm.shop.service.OutboundOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "出库管理", description = "商家发货仓出库单接口")
@RestController
@RequestMapping("/api/outbound-orders")
public class OutboundOrderController {

    @Autowired
    private OutboundOrderService outboundOrderService;

    @Operation(summary = "查询出库单列表")
    @GetMapping
    public Result<List<OutboundOrder>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String status) {
        return Result.success(outboundOrderService.listByWarehouseAndShop(warehouseId, shopId, status));
    }

    @Operation(summary = "获取出库单详情")
    @GetMapping("/{id}")
    public Result<OutboundOrder> getById(@PathVariable Long id) {
        return Result.success(outboundOrderService.getById(id));
    }

    @Operation(summary = "获取出库单明细")
    @GetMapping("/{id}/items")
    public Result<List<OutboundOrderItem>> getItems(@PathVariable Long id) {
        return Result.success(outboundOrderService.getItems(id));
    }

    @Operation(summary = "创建出库单")
    @PostMapping
    public Result<OutboundOrder> create(@RequestBody Map<String, Object> body) {
        OutboundOrder order = parseOrder(body);
        List<OutboundOrderItem> items = parseItems(body);
        return Result.success(outboundOrderService.create(order, items));
    }

    @Operation(summary = "开始出库（拣货）")
    @PutMapping("/{id}/start")
    public Result<OutboundOrder> start(@PathVariable Long id) {
        return Result.success(outboundOrderService.startProcessing(id));
    }

    @Operation(summary = "完成出库（扣减库存）")
    @PutMapping("/{id}/complete")
    public Result<OutboundOrder> complete(@PathVariable Long id) {
        return Result.success(outboundOrderService.complete(id));
    }

    @Operation(summary = "取消出库单")
    @PutMapping("/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable Long id,
                                   @RequestParam(defaultValue = "") String reason) {
        return Result.success(outboundOrderService.cancel(id, reason));
    }

    @SuppressWarnings("unchecked")
    private OutboundOrder parseOrder(Map<String, Object> body) {
        OutboundOrder order = new OutboundOrder();
        if (body.get("warehouseId") != null) order.setWarehouseId(Long.parseLong(body.get("warehouseId").toString()));
        if (body.get("shopId") != null) order.setShopId(Long.parseLong(body.get("shopId").toString()));
        if (body.get("destType") != null) order.setDestType(body.get("destType").toString());
        if (body.get("relatedId") != null) order.setRelatedId(Long.parseLong(body.get("relatedId").toString()));
        if (body.get("remark") != null) order.setRemark(body.get("remark").toString());
        return order;
    }

    @SuppressWarnings("unchecked")
    private List<OutboundOrderItem> parseItems(Map<String, Object> body) {
        Object rawItems = body.get("items");
        if (rawItems instanceof List) {
            List<Map<String, Object>> itemList = (List<Map<String, Object>>) rawItems;
            return itemList.stream().map(m -> {
                OutboundOrderItem item = new OutboundOrderItem();
                if (m.get("productId") != null) item.setProductId(Long.parseLong(m.get("productId").toString()));
                if (m.get("productName") != null) item.setProductName(m.get("productName").toString());
                if (m.get("quantity") != null) item.setQuantity(Integer.parseInt(m.get("quantity").toString()));
                if (m.get("locationId") != null) item.setLocationId(Long.parseLong(m.get("locationId").toString()));
                return item;
            }).collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }
}
