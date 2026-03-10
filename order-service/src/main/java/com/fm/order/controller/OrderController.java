package com.fm.order.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.order.dto.CreateOrderRequestDTO;
import com.fm.order.dto.OrderDetailDTO;
import com.fm.order.entity.Order;
import com.fm.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单管理Controller
 * 接口设计：
 * POST   /orders               - 创建订单
 * GET    /orders/{id}          - 获取订单详情
 * GET    /orders/my            - 获取我的订单列表（顾客）
 * GET    /orders/shop/{shopId} - 获取商户订单列表
 * PUT    /orders/{id}/status   - 更新订单状态（商户/管理员）
 * PUT    /orders/{id}/cancel   - 取消订单
 * PUT    /orders/{id}/pay      - 支付订单
 */
@Tag(name = "订单管理", description = "订单相关接口")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 创建订单
     * POST /orders
     */
    @Operation(summary = "创建订单", description = "顾客创建新订单")
    @PostMapping
    public Result<OrderDetailDTO> createOrder(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @RequestBody CreateOrderRequestDTO request) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 只有顾客角色可以创建订单
        if (!"customer".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有顾客可以创建订单");
        }
        Long customerId = Long.parseLong(userIdHeader);
        OrderDetailDTO orderDetail = orderService.createOrder(customerId, request);
        return Result.success(orderDetail);
    }

    /**
     * 获取订单详情
     * GET /orders/{id}
     */
    @Operation(summary = "获取订单详情", description = "根据订单ID获取订单详情及订单项")
    @GetMapping("/{id}")
    public Result<OrderDetailDTO> getOrderDetail(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "订单ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        OrderDetailDTO orderDetail = orderService.getOrderDetail(id);
        if (orderDetail == null) {
            return Result.error("订单不存在");
        }

        // 管理员可以查看所有订单，顾客只能查看自己的订单，商户只能查看自己店铺的订单
        if (!"admin".equals(roleCode)) {
            Long userId = Long.parseLong(userIdHeader);
            Order order = orderDetail.getOrder();
            if ("customer".equals(roleCode) && !order.getCustomerId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他顾客的订单");
            }
            if ("shop".equals(roleCode) && !order.getShopId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他商户的订单");
            }
        }

        return Result.success(orderDetail);
    }

    /**
     * 获取我的订单列表（顾客）
     * GET /orders/my
     */
    @Operation(summary = "获取我的订单列表", description = "顾客获取自己的订单列表")
    @GetMapping("/my")
    public Result<List<Order>> getMyOrders(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"customer".equals(roleCode) && !"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问");
        }
        Long customerId = Long.parseLong(userIdHeader);
        List<Order> orders = orderService.getOrdersByCustomerId(customerId);
        return Result.success(orders);
    }

    /**
     * 获取商户订单列表
     * GET /orders/shop/{shopId}
     */
    @Operation(summary = "获取商户订单列表", description = "商户获取自己店铺的订单列表")
    @GetMapping("/shop/{shopId}")
    public Result<List<Order>> getShopOrders(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "商户ID", required = true)
            @PathVariable Long shopId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 管理员可以查看所有商户订单，商户只能查看自己的订单
        if (!"admin".equals(roleCode)) {
            Long userId = Long.parseLong(userIdHeader);
            if (!"shop".equals(roleCode) || !shopId.equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他商户的订单");
            }
        }
        List<Order> orders = orderService.getOrdersByShopId(shopId);
        return Result.success(orders);
    }

    /**
     * 更新订单状态（商户/管理员操作）
     * PUT /orders/{id}/status
     * 请求体：{"orderStatus": 3}
     */
    @Operation(summary = "更新订单状态", description = "商户或管理员更新订单状态")
    @PutMapping("/{id}/status")
    public Result<Boolean> updateOrderStatus(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "订单ID", required = true)
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode) && !"shop".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权更新订单状态");
        }
        Integer orderStatus = body.get("orderStatus");
        if (orderStatus == null) {
            return Result.error("订单状态不能为空");
        }
        boolean success = orderService.updateOrderStatus(id, orderStatus);
        return Result.success(success);
    }

    /**
     * 取消订单
     * PUT /orders/{id}/cancel
     * 请求体：{"cancelReason": "不想要了"}
     */
    @Operation(summary = "取消订单", description = "顾客或管理员取消订单")
    @PutMapping("/{id}/cancel")
    public Result<Boolean> cancelOrder(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "订单ID", required = true)
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 管理员和顾客可以取消订单
        if (!"admin".equals(roleCode) && !"customer".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权取消订单");
        }
        // 顾客只能取消自己的订单
        if ("customer".equals(roleCode)) {
            OrderDetailDTO orderDetail = orderService.getOrderDetail(id);
            if (orderDetail == null) {
                return Result.error("订单不存在");
            }
            Long userId = Long.parseLong(userIdHeader);
            if (!orderDetail.getOrder().getCustomerId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权取消其他顾客的订单");
            }
        }
        String cancelReason = body.get("cancelReason");
        boolean success = orderService.cancelOrder(id, cancelReason);
        return Result.success(success);
    }

    /**
     * 支付订单
     * PUT /orders/{id}/pay
     */
    @Operation(summary = "支付订单", description = "顾客支付订单")
    @PutMapping("/{id}/pay")
    public Result<Boolean> payOrder(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "订单ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"customer".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有顾客可以支付订单");
        }
        // 验证订单归属
        OrderDetailDTO orderDetail = orderService.getOrderDetail(id);
        if (orderDetail == null) {
            return Result.error("订单不存在");
        }
        Long userId = Long.parseLong(userIdHeader);
        if (!orderDetail.getOrder().getCustomerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权支付其他顾客的订单");
        }
        boolean success = orderService.payOrder(id);
        return Result.success(success);
    }
}

