package com.fm.driver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fm.common.dto.PageResult;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.driver.entity.OrderDelivery;
import com.fm.driver.feign.OrderFeignClient;
import com.fm.driver.mapper.OrderDeliveryMapper;
import com.fm.driver.service.DeliveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Date;
import java.util.HashMap;
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
    
    @Override
    public PageResult<OrderDelivery> getPendingDeliveries(Long current, Long size) {
        Page<OrderDelivery> page = new Page<>(current, size);
        LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();
        // 只查询待接单的订单（delivery_status=0）
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
    public PageResult<OrderDelivery> getMyDeliveries(Long driverId, Long current, Long size, Integer status, String sortField, String sortOrder) {
        Page<OrderDelivery> page = new Page<>(current, size);
        LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDelivery::getDriverId, driverId);
        
        // 状态筛选
        if (status != null) {
            wrapper.eq(OrderDelivery::getDeliveryStatus, status);
        }
        
        // 排序
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
    public OrderDelivery acceptDelivery(Long deliveryId, Long driverId, Long vehicleId) {
        OrderDelivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "配送记录不存在");
        }
        
        // 检查是否已被接单
        if (delivery.getDeliveryStatus() != 0) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "该订单已被接单或状态不正确");
        }
        
        // 更新配送状态
        delivery.setDriverId(driverId);
        delivery.setVehicleId(vehicleId);
        delivery.setDeliveryStatus(1);  // 已接单
        delivery.setAcceptTime(new Date());
        deliveryMapper.updateById(delivery);
        
        return delivery;
    }
    
    @Override
    public OrderDelivery updateDeliveryStatus(Long deliveryId, Long driverId, Integer status, String remark) {
        OrderDelivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "配送记录不存在");
        }
        
        // 验证权限：只能更新自己接的订单
        if (!delivery.getDriverId().equals(driverId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        // 验证状态流转
        Integer currentStatus = delivery.getDeliveryStatus();
        if (status == 2) {  // 运输中
            if (currentStatus != 1) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "只能从已接单状态更新为运输中");
            }
            delivery.setPickupTime(new Date());
        } else if (status == 3) {  // 已送达
            if (currentStatus != 2) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "只能从运输中状态更新为已送达");
            }
            delivery.setDeliveryTime(new Date());
            // 调用订单服务，更新订单状态为已完成
            try {
                Map<String, Integer> body = new java.util.HashMap<>();
                body.put("orderStatus", 4);  // 4=已完成
                orderFeignClient.updateOrderStatus(delivery.getOrderId(), body);
            } catch (Exception e) {
                // 记录日志，但不影响配送状态更新
                System.err.println("更新订单状态失败: " + e.getMessage());
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
    
    @Override
    public OrderDelivery cancelDelivery(Long deliveryId, Long driverId, String cancelReason) {
        OrderDelivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "配送记录不存在");
        }
        
        // 验证权限
        if (!delivery.getDriverId().equals(driverId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        // 只能取消已接单或运输中的订单
        if (delivery.getDeliveryStatus() != 1 && delivery.getDeliveryStatus() != 2) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "只能取消已接单或运输中的订单");
        }
        
        delivery.setDeliveryStatus(4);  // 已取消
        delivery.setCancelTime(new Date());
        delivery.setCancelReason(cancelReason);
        deliveryMapper.updateById(delivery);
        
        return delivery;
    }
    
    @Override
    public OrderDelivery createDelivery(Long orderId, String deliveryAddress, String receiverName, String receiverPhone) {
        // 检查是否已存在配送记录
        OrderDelivery existing = getDeliveryByOrderId(orderId);
        if (existing != null) {
            return existing;
        }
        
        // 创建新的配送记录
        OrderDelivery delivery = new OrderDelivery();
        delivery.setOrderId(orderId);
        delivery.setDeliveryStatus(0);  // 待接单
        delivery.setDeliveryAddress(deliveryAddress);
        delivery.setReceiverName(receiverName);
        delivery.setReceiverPhone(receiverPhone);
        deliveryMapper.insert(delivery);
        
        return delivery;
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
                if (isAsc) {
                    wrapper.orderByAsc(OrderDelivery::getAcceptTime);
                } else {
                    wrapper.orderByDesc(OrderDelivery::getAcceptTime);
                }
                break;
            case "deliverytime":
            case "delivery_time":
                if (isAsc) {
                    wrapper.orderByAsc(OrderDelivery::getDeliveryTime);
                } else {
                    wrapper.orderByDesc(OrderDelivery::getDeliveryTime);
                }
                break;
            case "createtime":
            case "create_time":
            default:
                if (isAsc) {
                    wrapper.orderByAsc(OrderDelivery::getCreateTime);
                } else {
                    wrapper.orderByDesc(OrderDelivery::getCreateTime);
                }
                break;
        }
    }
}

