package com.fm.shop.controller;

import com.fm.common.result.Result;
import com.fm.shop.entity.InboundOrder;
import com.fm.shop.entity.InboundOrderItem;
import com.fm.shop.service.InboundOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "入库管理", description = "商家发货仓入库单接口")
@RestController
@RequestMapping("/api/inbound-orders")
public class InboundOrderController {

    @Autowired
    private InboundOrderService inboundOrderService;

    @Operation(summary = "查询入库单列表")
    @GetMapping
    public Result<List<InboundOrder>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String status) {
        return Result.success(inboundOrderService.listByWarehouseAndShop(warehouseId, shopId, status));
    }

    @Operation(summary = "获取入库单详情")
    @GetMapping("/{id}")
    public Result<InboundOrder> getById(@PathVariable Long id) {
        return Result.success(inboundOrderService.getById(id));
    }

    @Operation(summary = "获取入库单明细")
    @GetMapping("/{id}/items")
    public Result<List<InboundOrderItem>> getItems(@PathVariable Long id) {
        return Result.success(inboundOrderService.getItems(id));
    }

    @Operation(summary = "创建入库单")
    @PostMapping
    public Result<InboundOrder> create(@RequestBody Map<String, Object> body) {
        InboundOrder order = parseOrder(body);
        List<InboundOrderItem> items = parseItems(body);
        return Result.success(inboundOrderService.create(order, items));
    }

    @Operation(summary = "开始入库")
    @PutMapping("/{id}/start")
    public Result<InboundOrder> start(@PathVariable Long id) {
        return Result.success(inboundOrderService.startProcessing(id));
    }

    @Operation(summary = "完成入库（含实际数量录入）")
    @PutMapping("/{id}/complete")
    public Result<InboundOrder> complete(@PathVariable Long id,
                                          @RequestBody List<InboundOrderItem> actualItems) {
        return Result.success(inboundOrderService.complete(id, actualItems));
    }

    @Operation(summary = "取消入库单")
    @PutMapping("/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable Long id,
                                   @RequestParam(defaultValue = "") String reason) {
        return Result.success(inboundOrderService.cancel(id, reason));
    }

    @SuppressWarnings("unchecked")
    private InboundOrder parseOrder(Map<String, Object> body) {
        InboundOrder order = new InboundOrder();
        if (body.get("warehouseId") != null) order.setWarehouseId(Long.parseLong(body.get("warehouseId").toString()));
        if (body.get("shopId") != null) order.setShopId(Long.parseLong(body.get("shopId").toString()));
        if (body.get("sourceType") != null) order.setSourceType(body.get("sourceType").toString());
        if (body.get("remark") != null) order.setRemark(body.get("remark").toString());
        return order;
    }

    @SuppressWarnings("unchecked")
    private List<InboundOrderItem> parseItems(Map<String, Object> body) {
        Object rawItems = body.get("items");
        if (rawItems instanceof List) {
            List<Map<String, Object>> itemList = (List<Map<String, Object>>) rawItems;
            return itemList.stream().map(m -> {
                InboundOrderItem item = new InboundOrderItem();
                if (m.get("productId") != null) item.setProductId(Long.parseLong(m.get("productId").toString()));
                if (m.get("productName") != null) item.setProductName(m.get("productName").toString());
                if (m.get("expectedQty") != null) item.setExpectedQty(Integer.parseInt(m.get("expectedQty").toString()));
                if (m.get("locationId") != null) item.setLocationId(Long.parseLong(m.get("locationId").toString()));
                return item;
            }).collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }
}
