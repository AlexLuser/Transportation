package com.fm.order.controller;

import com.fm.common.dto.PageResult;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.order.dto.CreateOrderRequestDTO;
import com.fm.order.dto.OrderDetailDTO;
import com.fm.order.dto.ShipOrderRequestDTO;
import com.fm.order.entity.Order;
import com.fm.order.feign.CustomerFeignClient;
import com.fm.order.feign.ShopFeignClient;
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
 * PUT    /orders/{id}/pay      - 支付订单（毕设用：直接置为已支付）
 */
@Tag(name = "订单管理", description = "订单相关接口")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    // 注入 Feign 客户端，用于控制层做 userId → 业务ID 转换
    @Autowired
    private CustomerFeignClient customerFeignClient;

    @Autowired
    private ShopFeignClient shopFeignClient;

    // ========== 私有辅助方法：userId → customerId ==========

    /**
     * 根据 userId 获取 customerId，找不到则抛异常
     */
    private Long resolveCustomerId(Long userId) {
        Result<Map<String, Object>> result = customerFeignClient.getCustomerByUserId(userId);
        if (result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "顾客信息不存在，请先完善个人资料");
        }
        return Long.valueOf(result.getData().get("id").toString());
    }

    /**
     * 根据 userId 获取 shopId，找不到则抛异常
     */
    private Long resolveShopId(Long userId) {
        Result<Map<String, Object>> result = shopFeignClient.getShopByUserId(userId);
        if (result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "商户信息不存在，请先完善商户资料");
        }
        return Long.valueOf(result.getData().get("id").toString());
    }

    // ========== 业务接口 ==========

    /**
     * 管理员分页查询全部订单（按创建时间倒序，支持按状态过滤）
     * GET /orders/admin/all
     */
    @Operation(summary = "管理员查询全部订单", description = "仅管理员可用；分页返回全部订单，可按订单状态过滤")
    @GetMapping("/admin/all")
    public Result<PageResult<Order>> getAllOrders(
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "15") Long size,
            @RequestParam(value = "status", required = false) Integer status) {
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问");
        }
        return Result.success(orderService.getAllOrders(current, size, status));
    }

    /**
     * 根据订单号精确查询订单（管理员用）
     * GET /orders/no/{orderNo}
     */
    @Operation(summary = "按订单号查询", description = "仅管理员可用；根据订单号精确查询单条订单")
    @GetMapping("/no/{orderNo}")
    public Result<Order> getOrderByNo(
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @PathVariable String orderNo) {
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问");
        }
        Order order = orderService.getOrderByNo(orderNo);
        if (order == null) {
            return Result.error("订单号不存在");
        }
        return Result.success(order);
    }

    /**
     * 创建订单（B1：userId → customerId）
     * POST /orders
     */
    @Operation(summary = "创建订单", description = "顾客创建新订单，系统自动将 userId 转换为 customerId")
    @PostMapping
    public Result<OrderDetailDTO> createOrder(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @RequestBody CreateOrderRequestDTO request) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"customer".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有顾客可以创建订单");
        }
        Long userId = Long.parseLong(userIdHeader);
        // B1: userId → customerId 转换（避免把 userId 直接当业务主键）
        Long customerId = resolveCustomerId(userId);
        OrderDetailDTO orderDetail = orderService.createOrder(customerId, request);
        return Result.success(orderDetail);
    }

    /**
     * 获取订单详情（B2：权限校验使用正确的业务ID）
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

        // B2：管理员可看全部；顾客/商户需验证业务ID（不能直接用 userId 比对）
        if (!"admin".equals(roleCode)) {
            Long userId = Long.parseLong(userIdHeader);
            Order order = orderDetail.getOrder();
            if ("customer".equals(roleCode)) {
                Long customerId = resolveCustomerId(userId);
                if (!order.getCustomerId().equals(customerId)) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他顾客的订单");
                }
            } else if ("shop".equals(roleCode)) {
                Long shopId = resolveShopId(userId);
                if (!order.getShopId().equals(shopId)) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他商户的订单");
                }
            }
        }

        return Result.success(orderDetail);
    }

    /**
     * 获取我的订单列表（B2：按 customerId 查询）
     * GET /orders/my
     */
    @Operation(summary = "获取我的订单列表", description = "顾客获取自己的订单列表（按 customerId 查询）")
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
        Long userId = Long.parseLong(userIdHeader);
        // B2: userId → customerId，按真实 customerId 查询订单
        Long customerId = resolveCustomerId(userId);
        List<Order> orders = orderService.getOrdersByCustomerId(customerId);
        return Result.success(orders);
    }

    /**
     * 获取商户订单列表（B2：按 shopId 验证身份）
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
        // B2：商户角色通过 userId → shopId 验证，不允许直接用 userId 比对 shopId
        if (!"admin".equals(roleCode)) {
            Long userId = Long.parseLong(userIdHeader);
            if (!"shop".equals(roleCode)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他商户的订单");
            }
            Long userShopId = resolveShopId(userId);
            if (!shopId.equals(userShopId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他商户的订单");
            }
        }
        List<Order> orders = orderService.getOrdersByShopId(shopId);
        return Result.success(orders);
    }

    /**
     * 更新订单状态（B3：商户操作前验证 shopId 归属）
     * PUT /orders/{id}/status
     * 请求体：{"orderStatus": 2} 表示发货（订单进入待揽件）；3/4 一般为系统/运输员侧同步
     */
    @Operation(summary = "更新订单状态", description = "0待支付 1待发货 2待揽件 3派送中 4已完成 5已取消；商户仅可传 2 发货")
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
        if ("shop".equals(roleCode) && !Integer.valueOf(2).equals(orderStatus)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "商户仅可执行发货（orderStatus=2 待揽件）");
        }

        // B3：商户发货时，验证当前用户确实是该订单所属商户
        if ("shop".equals(roleCode)) {
            Long userId = Long.parseLong(userIdHeader);
            Long userShopId = resolveShopId(userId);
            OrderDetailDTO orderDetail = orderService.getOrderDetail(id);
            if (orderDetail == null) {
                return Result.error("订单不存在");
            }
            if (!orderDetail.getOrder().getShopId().equals(userShopId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他商户的订单");
            }
        }

        boolean success = orderService.updateOrderStatus(id, orderStatus);
        return Result.success(success);
    }

    /**
     * 运输员取消接单后由 driver-service 内部调用：订单 派送中(3) → 待揽件(2)
     */
    @Operation(summary = "内部：订单回到待揽件", description = "仅管理员或服务间调用，用于司机取消接单后回滚订单状态")
    @PutMapping("/{id}/reopen-pickup")
    public Result<Boolean> reopenOrderToPendingPickup(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权执行该操作");
        }
        boolean ok = orderService.reopenOrderToPendingPickup(id);
        return Result.success(ok);
    }

    /**
     * 取消订单（B2：使用 customerId 验证归属）
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
        if (!"admin".equals(roleCode) && !"customer".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权取消订单");
        }
        // B2：顾客只能取消自己的订单（用 customerId 比对，不用 userId）
        if ("customer".equals(roleCode)) {
            OrderDetailDTO orderDetail = orderService.getOrderDetail(id);
            if (orderDetail == null) {
                return Result.error("订单不存在");
            }
            Long userId = Long.parseLong(userIdHeader);
            Long customerId = resolveCustomerId(userId);
            if (!orderDetail.getOrder().getCustomerId().equals(customerId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权取消其他顾客的订单");
            }
        }
        String cancelReason = body.get("cancelReason");
        boolean success = orderService.cancelOrder(id, cancelReason);
        return Result.success(success);
    }

    /**
     * 支付订单（毕设用：直接置为已支付/待发货，无真实支付）
     * PUT /orders/{id}/pay
     */
    @Operation(summary = "支付订单", description = "顾客支付订单（毕设模拟：直接标记为已支付）")
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
        // B2：验证订单归属使用 customerId（不是 userId）
        OrderDetailDTO orderDetail = orderService.getOrderDetail(id);
        if (orderDetail == null) {
            return Result.error("订单不存在");
        }
        Long userId = Long.parseLong(userIdHeader);
        Long customerId = resolveCustomerId(userId);
        if (!orderDetail.getOrder().getCustomerId().equals(customerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权支付其他顾客的订单");
        }
        boolean success = orderService.payOrder(id);
        return Result.success(success);
    }

    /**
     * 商户发货（含仓库选择）
     * POST /orders/{id}/ship
     * 商户在发货弹窗中选择发货仓库后调用此接口
     */
    @Operation(summary = "商户发货", description = "商户选择仓库发货，系统自动分配Hub并判断是否跨城")
    @PostMapping("/{id}/ship")
    public Result<Boolean> shipOrder(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @PathVariable Long id,
            @RequestBody ShipOrderRequestDTO req) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"shop".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有商户可以发货");
        }
        Long userId = Long.parseLong(userIdHeader);
        Long shopId = resolveShopId(userId);
        boolean success = orderService.shipOrder(id, shopId, req.getWarehouseId());
        return Result.success(success);
    }

    /**
     * 顾客签收订单（待签收:6 → 已完成:4）
     * PUT /orders/{id}/sign
     */
    @Operation(summary = "顾客签收订单", description = "订单处于待签收状态时，顾客确认签收，订单变为已完成")
    @PutMapping("/{id}/sign")
    public Result<Boolean> signOrder(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"customer".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有顾客可以签收订单");
        }
        Long userId = Long.parseLong(userIdHeader);
        Long customerId = resolveCustomerId(userId);
        boolean success = orderService.signOrder(id, customerId);
        return Result.success(success);
    }

    /**
     * 顾客软删除订单（对顾客隐藏，商户/管理员仍可见）
     * DELETE /orders/{id}
     */
    @Operation(summary = "删除订单（顾客）", description = "顾客软删除已取消的订单，订单对顾客不再显示")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteOrder(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "订单ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"customer".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有顾客可以删除订单");
        }
        Long userId = Long.parseLong(userIdHeader);
        Long customerId = resolveCustomerId(userId);
        boolean success = orderService.deleteOrderByCustomer(id, customerId);
        return Result.success(success);
    }
}
