package com.fm.driver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fm.common.dto.PageResult;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.driver.entity.OrderDelivery;
import com.fm.driver.feign.LogisticsFeignClient;
import com.fm.driver.feign.OrderFeignClient;
import com.fm.driver.mapper.OrderDeliveryMapper;
import com.fm.driver.service.DeliveryService;
import com.fm.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配送服务实现类
 */
@Service
public class DeliveryServiceImpl implements DeliveryService {

    @Autowired
    private OrderDeliveryMapper deliveryMapper;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private LogisticsFeignClient logisticsFeignClient;

    // ----------------------------------------------------------------
    //  查询类
    // ----------------------------------------------------------------

    @Override
    public PageResult<OrderDelivery> getPendingDeliveries(Long current, Long size) {
        Page<OrderDelivery> page = new Page<>(current, size);
        LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDelivery::getDeliveryStatus, 0);
        wrapper.orderByDesc(OrderDelivery::getCreateTime);

        IPage<OrderDelivery> pageResult = deliveryMapper.selectPage(page, wrapper);
        return new PageResult<>(
                pageResult.getCurrent(),
                pageResult.getSize(),
                pageResult.getTotal(),
                pageResult.getRecords()
        );
    }

    @Override
    public PageResult<OrderDelivery> getMyDeliveries(Long driverId, Long current, Long size,
                                                     Integer status, String sortField, String sortOrder) {
        Page<OrderDelivery> page = new Page<>(current, size);
        LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDelivery::getDriverId, driverId);
        if (status != null) {
            wrapper.eq(OrderDelivery::getDeliveryStatus, status);
        }
        applySort(wrapper, sortField, sortOrder);

        IPage<OrderDelivery> pageResult = deliveryMapper.selectPage(page, wrapper);
        return new PageResult<>(
                pageResult.getCurrent(),
                pageResult.getSize(),
                pageResult.getTotal(),
                pageResult.getRecords()
        );
    }

    @Override
    public OrderDelivery getDeliveryById(Long deliveryId) {
        return deliveryMapper.selectById(deliveryId);
    }

    @Override
    public OrderDelivery getDeliveryByOrderId(Long orderId) {
        LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDelivery::getOrderId, orderId);
        return deliveryMapper.selectOne(wrapper);
    }

    @Override
    public List<OrderDelivery> listInProgressDeliveries(Long driverId) {
        LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDelivery::getDriverId, driverId)
                .in(OrderDelivery::getDeliveryStatus, 1, 2)
                .orderByDesc(OrderDelivery::getAcceptTime);
        return deliveryMapper.selectList(wrapper);
    }

    // ----------------------------------------------------------------
    //  接单（B4：接单后绑定物流路线）
    // ----------------------------------------------------------------

    @Override
    public OrderDelivery acceptDelivery(Long deliveryId, Long driverId, Long vehicleId) {
        OrderDelivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "配送记录不存在");
        }
        if (delivery.getDeliveryStatus() != 0) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "该订单已被接单或状态不正确");
        }

        // 更新配送状态为已接单
        delivery.setDriverId(driverId);
        delivery.setVehicleId(vehicleId);
        delivery.setDeliveryStatus(1);  // 已接单
        delivery.setAcceptTime(new Date());
        deliveryMapper.updateById(delivery);

        // 同步订单状态 → 派送中(3)
        try {
            Map<String, Integer> body = new HashMap<>();
            body.put("orderStatus", 3);
            orderFeignClient.updateOrderStatus(delivery.getOrderId(), body, "system", "admin");
        } catch (Exception e) {
            System.err.println("接单后同步订单状态失败：" + e.getMessage());
        }

        // B4：接单后绑定物流路线（将 driverId 和 deliveryId 写入路线）
        try {
            Long routeId = getRouteIdByOrderId(delivery.getOrderId());
            if (routeId != null) {
                Map<String, Long> bindBody = new HashMap<>();
                bindBody.put("driverId", driverId);
                bindBody.put("deliveryId", deliveryId);
                logisticsFeignClient.bindDriver(routeId, bindBody);
            } else {
                System.err.println("B4警告：未找到订单 " + delivery.getOrderId() + " 对应的物流路线，跳过绑定");
            }
        } catch (Exception e) {
            // 物流绑定失败不回滚接单，仅记录日志（毕设容错）
            System.err.println("B4：绑定物流路线失败：" + e.getMessage());
        }

        return delivery;
    }

    // ----------------------------------------------------------------
    //  更新配送状态（B5：同步物流路线状态 & 订单状态）
    // ----------------------------------------------------------------

    @Override
    public OrderDelivery updateDeliveryStatus(Long deliveryId, Long driverId, Integer status, String remark) {
        OrderDelivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "配送记录不存在");
        }
        if (!delivery.getDriverId().equals(driverId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        Integer currentStatus = delivery.getDeliveryStatus();

        if (status == 2) {  // 运输中
            if (currentStatus != 1) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "只能从已接单状态更新为运输中");
            }
            delivery.setPickupTime(new Date());

            // B5：同步路线状态 → 运输中(1)
            syncRouteStatus(delivery.getOrderId(), 1);

        } else if (status == 3) {  // 已送达
            if (currentStatus != 2) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "只能从运输中状态更新为已送达");
            }
            delivery.setDeliveryTime(new Date());

            // B5：同步路线状态 → 已送达(2)
            syncRouteStatus(delivery.getOrderId(), 2);

            // B5：同步订单状态 → 已完成(4)
            try {
                Map<String, Integer> body = new HashMap<>();
                body.put("orderStatus", 4);
                // 内部服务间调用：userId 传非空值通过非空校验，roleCode 传 "admin" 通过角色校验
                orderFeignClient.updateOrderStatus(delivery.getOrderId(), body, "system", "admin");
            } catch (Exception e) {
                System.err.println("B5：更新订单状态失败：" + e.getMessage());
            }

        } else {
            throw new BusinessException(ResultCode.FAIL.getCode(), "不支持的状态更新");
        }

        delivery.setDeliveryStatus(status);
        if (StringUtils.hasText(remark)) {
            delivery.setRemark(remark);
        }
        deliveryMapper.updateById(delivery);

        return delivery;
    }

    // ----------------------------------------------------------------
    //  取消配送（B5：同步路线状态为异常）
    // ----------------------------------------------------------------

    @Override
    public OrderDelivery cancelDelivery(Long deliveryId, Long driverId, String cancelReason) {
        OrderDelivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "配送记录不存在");
        }
        if (!delivery.getDriverId().equals(driverId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (delivery.getDeliveryStatus() != 1 && delivery.getDeliveryStatus() != 2) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "只能取消已接单或运输中的订单");
        }

        Long orderId = delivery.getOrderId();
        String prevRemark = delivery.getRemark();
        String note = "【司机取消配送】" + (StringUtils.hasText(cancelReason) ? cancelReason : "");
        delivery.setDeliveryStatus(0);  // 回到待接单大厅
        delivery.setDriverId(null);
        delivery.setVehicleId(null);
        delivery.setAcceptTime(null);
        delivery.setPickupTime(null);
        delivery.setDeliveryTime(null);
        delivery.setCancelTime(null);
        delivery.setCancelReason(null);
        delivery.setRemark(StringUtils.hasText(prevRemark) ? prevRemark + "；" + note : note);

        // B5：同步路线状态 → 运输异常(3)（下次接单会再次 bindDriver 覆盖）
        syncRouteStatus(orderId, 3);

        deliveryMapper.updateById(delivery);

        try {
            orderFeignClient.reopenOrderToPendingPickup(orderId, "system", "admin");
        } catch (Exception e) {
            System.err.println("取消配送后同步订单状态失败：" + e.getMessage());
        }

        return delivery;
    }

    // ----------------------------------------------------------------
    //  创建配送记录（订单服务内部调用）
    // ----------------------------------------------------------------

    @Override
    public OrderDelivery createDelivery(Long orderId, String deliveryAddress,
                                        String receiverName, String receiverPhone) {
        // 幂等：已存在则直接返回
        OrderDelivery existing = getDeliveryByOrderId(orderId);
        if (existing != null) {
            return existing;
        }

        OrderDelivery delivery = new OrderDelivery();
        delivery.setOrderId(orderId);
        delivery.setDeliveryStatus(0);  // 待接单
        delivery.setDeliveryAddress(deliveryAddress);
        delivery.setReceiverName(receiverName);
        delivery.setReceiverPhone(receiverPhone);
        deliveryMapper.insert(delivery);

        return delivery;
    }

    // ----------------------------------------------------------------
    //  私有辅助方法
    // ----------------------------------------------------------------

    /**
     * 根据 orderId 查询 logistics_route 的 id（routeId）
     * logistics-service 返回 RouteDetailDTO，结构为 {route: {id: x, ...}, nodes: [...], ...}
     */
    private Long getRouteIdByOrderId(Long orderId) {
        try {
            Result<Map<String, Object>> result = logisticsFeignClient.getRouteByOrderId(orderId, "system");
            if (result.getCode() == 200 && result.getData() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> routeDetailMap = result.getData();
                Object routeObj = routeDetailMap.get("route");
                if (routeObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> routeMap = (Map<String, Object>) routeObj;
                    Object idObj = routeMap.get("id");
                    if (idObj != null) {
                        return Long.valueOf(idObj.toString());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("查询物流路线失败（orderId=" + orderId + "）：" + e.getMessage());
        }
        return null;
    }

    /**
     * 同步物流路线状态（内部服务调用，roleCode 使用 "driver"）
     */
    private void syncRouteStatus(Long orderId, int routeStatus) {
        try {
            Long routeId = getRouteIdByOrderId(orderId);
            if (routeId != null) {
                Map<String, Integer> statusBody = new HashMap<>();
                statusBody.put("status", routeStatus);
                logisticsFeignClient.updateRouteStatus(routeId, statusBody, "system", "driver");
            }
        } catch (Exception e) {
            System.err.println("B5：同步路线状态失败（orderId=" + orderId + "）：" + e.getMessage());
        }
    }

    /**
     * 应用排序规则
     */
    private void applySort(LambdaQueryWrapper<OrderDelivery> wrapper, String sortField, String sortOrder) {
        if (!StringUtils.hasText(sortField)) {
            sortField = "createTime";
        }
        if (!StringUtils.hasText(sortOrder)) {
            sortOrder = "desc";
        }
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortField.toLowerCase()) {
            case "accepttime":
            case "accept_time":
                if (isAsc) wrapper.orderByAsc(OrderDelivery::getAcceptTime);
                else       wrapper.orderByDesc(OrderDelivery::getAcceptTime);
                break;
            case "deliverytime":
            case "delivery_time":
                if (isAsc) wrapper.orderByAsc(OrderDelivery::getDeliveryTime);
                else       wrapper.orderByDesc(OrderDelivery::getDeliveryTime);
                break;
            case "createtime":
            case "create_time":
            default:
                if (isAsc) wrapper.orderByAsc(OrderDelivery::getCreateTime);
                else       wrapper.orderByDesc(OrderDelivery::getCreateTime);
                break;
        }
    }
}
