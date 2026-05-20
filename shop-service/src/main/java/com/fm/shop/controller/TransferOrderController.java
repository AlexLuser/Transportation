package com.fm.shop.controller;

import com.fm.common.result.Result;
import com.fm.shop.entity.TransferOrder;
import com.fm.shop.entity.TransferOrderItem;
import com.fm.shop.service.TransferOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "调拨管理", description = "商家跨仓调拨接口")
@RestController
@RequestMapping("/api/transfer-orders")
public class TransferOrderController {

    @Autowired
    private TransferOrderService transferOrderService;

    @Operation(summary = "查询调拨单列表")
    @GetMapping
    public Result<List<TransferOrder>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String status) {
        return Result.success(transferOrderService.listByShop(shopId, status));
    }

    @Operation(summary = "获取调拨单详情")
    @GetMapping("/{id}")
    public Result<TransferOrder> getById(@PathVariable Long id) {
        return Result.success(transferOrderService.getById(id));
    }

    @Operation(summary = "获取调拨单明细")
    @GetMapping("/{id}/items")
    public Result<List<TransferOrderItem>> getItems(@PathVariable Long id) {
        return Result.success(transferOrderService.getItems(id));
    }

    @Operation(summary = "创建调拨单")
    @PostMapping
    public Result<TransferOrder> create(@RequestBody Map<String, Object> body) {
        TransferOrder order = parseOrder(body);
        List<TransferOrderItem> items = parseItems(body);
        return Result.success(transferOrderService.create(order, items));
    }

    @Operation(summary = "审批调拨单")
    @PutMapping("/{id}/approve")
    public Result<TransferOrder> approve(@PathVariable Long id) {
        return Result.success(transferOrderService.approve(id));
    }

    @Operation(summary = "完成调拨（执行库存转移）")
    @PutMapping("/{id}/complete")
    public Result<TransferOrder> complete(@PathVariable Long id) {
        return Result.success(transferOrderService.complete(id));
    }

    @Operation(summary = "取消调拨单")
    @PutMapping("/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable Long id,
                                   @RequestParam(defaultValue = "") String reason) {
        return Result.success(transferOrderService.cancel(id, reason));
    }

    @SuppressWarnings("unchecked")
    private TransferOrder parseOrder(Map<String, Object> body) {
        TransferOrder order = new TransferOrder();
        if (body.get("shopId") != null) order.setShopId(Long.parseLong(body.get("shopId").toString()));
        if (body.get("srcWarehouseId") != null) order.setSrcWarehouseId(Long.parseLong(body.get("srcWarehouseId").toString()));
        if (body.get("dstWarehouseId") != null) order.setDstWarehouseId(Long.parseLong(body.get("dstWarehouseId").toString()));
        if (body.get("remark") != null) order.setRemark(body.get("remark").toString());
        return order;
    }

    @SuppressWarnings("unchecked")
    private List<TransferOrderItem> parseItems(Map<String, Object> body) {
        Object rawItems = body.get("items");
        if (rawItems instanceof List) {
            List<Map<String, Object>> itemList = (List<Map<String, Object>>) rawItems;
            return itemList.stream().map(m -> {
                TransferOrderItem item = new TransferOrderItem();
                if (m.get("productId") != null) item.setProductId(Long.parseLong(m.get("productId").toString()));
                if (m.get("productName") != null) item.setProductName(m.get("productName").toString());
                if (m.get("quantity") != null) item.setQuantity(Integer.parseInt(m.get("quantity").toString()));
                if (m.get("srcLocationId") != null) item.setSrcLocationId(Long.parseLong(m.get("srcLocationId").toString()));
                if (m.get("dstLocationId") != null) item.setDstLocationId(Long.parseLong(m.get("dstLocationId").toString()));
                return item;
            }).collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }
}
