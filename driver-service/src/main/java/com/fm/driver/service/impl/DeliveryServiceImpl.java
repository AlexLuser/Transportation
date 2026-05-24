package com.fm.driver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fm.common.dto.PageResult;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.driver.config.RabbitMQConfig;
import com.fm.driver.entity.OrderDelivery;
import com.fm.driver.mapper.OrderDeliveryMapper;
import com.fm.driver.mq.BindRouteMessage;
import com.fm.driver.mq.HubArrivalMessage;
import com.fm.driver.mq.LastMileActivateMessage;
import com.fm.driver.mq.ReopenOrderMessage;
import com.fm.driver.mq.RouteStatusMessage;
import com.fm.driver.mq.UpdateOrderStatusMessage;
import com.fm.driver.service.DeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 配送服务实现类
 */
@Slf4j
@Service
public class DeliveryServiceImpl implements DeliveryService {

    @Autowired
    private OrderDeliveryMapper deliveryMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // ----------------------------------------------------------------
    //  查询类
    // ----------------------------------------------------------------

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
    @Cacheable(value = "inProgressDeliveries", key = "#driverId")
    public List<OrderDelivery> listInProgressDeliveries(Long driverId) {
        LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDelivery::getDriverId, driverId)
                .in(OrderDelivery::getDeliveryStatus, 1, 2)
                .orderByDesc(OrderDelivery::getAcceptTime);
        return deliveryMapper.selectList(wrapper);
    }

    // ----------------------------------------------------------------
    //  更新配送状态（B5：同步物流路线状态 & 订单状态）
    // ----------------------------------------------------------------

    @Override
    @CacheEvict(value = "inProgressDeliveries", key = "#driverId")
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

            // #11: 发送 MQ 消息，B5 异步同步路线状态 → 运输中(1)
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_ROUTE_STATUS_UPDATE,
                new RouteStatusMessage(delivery.getOrderId(), 1)
            );

        } else if (status == 3) {  // 已送达
            if (currentStatus != 2) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "只能从运输中状态更新为已送达");
            }
            delivery.setDeliveryTime(new Date());

            // #11: 发送 MQ 消息，B5 异步同步路线状态 → 已送达(2)
            // orderId 为 null 时（多停靠末端或干线）跳过，由 Controller 层或逐站完成时处理
            if (delivery.getOrderId() != null) {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ROUTING_ROUTE_STATUS_UPDATE,
                    new RouteStatusMessage(delivery.getOrderId(), 2)
                );

                // #9: 发送 MQ 消息，B5 异步同步订单状态 → 待签收(6)，等顾客确认签收后转为已完成
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ROUTING_ORDER_STATUS_UPDATE,
                    new UpdateOrderStatusMessage(delivery.getOrderId(), 6)
                );
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
    @CacheEvict(value = "inProgressDeliveries", key = "#driverId")
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

        // #11: 发送 MQ 消息，B5 异步同步路线状态 → 运输异常(3)
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_ROUTE_STATUS_UPDATE,
            new RouteStatusMessage(orderId, 3)
        );

        deliveryMapper.updateById(delivery);

        // #12: 发送 MQ 消息，异步将订单回退为待揽件
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_ORDER_REOPEN,
            new ReopenOrderMessage(orderId)
        );

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
    //  Hub-and-Spoke 扩展：到达中转站
    // ----------------------------------------------------------------

    /**
     * 干线司机确认到达 Hub 中转站
     *
     * 操作：
     *   1. 将配送状态改为 3（已送达，对干线而言是"已到达Hub"）
     *   2. 向 logistics-service 发送 HubArrivalMessage，触发末端路线激活
     *
     * @param deliveryId 配送记录ID（dry-line 段）
     * @param driverId   当前司机ID（校验归属）
     */
    @Override
    @CacheEvict(value = "inProgressDeliveries", key = "#driverId")
    public OrderDelivery arriveAtHub(Long deliveryId, Long driverId) {
        OrderDelivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "配送记录不存在");
        }
        if (!delivery.getDriverId().equals(driverId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (delivery.getSegmentType() == null || delivery.getSegmentType() != 1) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "该配送记录不是干线任务，无法执行到达Hub操作");
        }
        if (delivery.getDeliveryStatus() != 2) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "请先更新配送状态为运输中，再确认到达Hub");
        }

        // 干线送达（到达Hub）
        delivery.setDeliveryStatus(3);
        delivery.setDeliveryTime(new Date());
        deliveryMapper.updateById(delivery);

        // #13: 通知 logistics-service 激活末端路线
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_HUB_ARRIVAL,
            new HubArrivalMessage(delivery.getBatchId(), delivery.getHubId(), driverId, deliveryId)
        );

        return delivery;
    }

    /**
     * 配送单创建（由 MQ #14 LastMileActivateMessage 触发）
     *
     * 兼容两种路线段：
     *   segmentType=1（干线）— createBatch 后立即触发，让干线出现在待接单大厅
     *   segmentType=2（末端）— 干线到达 Hub 后触发
     */
    public OrderDelivery createLastMileDelivery(LastMileActivateMessage msg) {
        log.info("[DEBUG][createLastMileDelivery] 收到消息: segmentType={}, orderId={}, batchId={}, routeId={}, preAssignedDriverId={}",
                msg.getSegmentType(), msg.getOrderId(), msg.getBatchId(), msg.getRouteId(), msg.getPreAssignedDriverId());
        // 幂等：干线按 batchId+segmentType=1 判断，末端按 orderId 判断
        int segType = msg.getSegmentType() != null ? msg.getSegmentType() : 2;
        if (segType == 1) {
            // 干线幂等：同一批次只创建一条干线待接单记录
            if (msg.getBatchId() != null) {
                LambdaQueryWrapper<OrderDelivery> q = new LambdaQueryWrapper<>();
                q.eq(OrderDelivery::getBatchId, msg.getBatchId())
                 .eq(OrderDelivery::getSegmentType, 1);
                if (deliveryMapper.selectCount(q) > 0) return deliveryMapper.selectOne(q);
            }
        } else {
            if (msg.getOrderId() != null) {
                OrderDelivery existing = getDeliveryByOrderId(msg.getOrderId());
                if (existing != null) return existing;
            }
        }

        OrderDelivery delivery = new OrderDelivery();
        delivery.setOrderId(msg.getOrderId());          // 干线为 null，末端为具体订单ID
        delivery.setDeliveryAddress(msg.getEndAddress());
        delivery.setReceiverName(msg.getReceiverName() != null ? msg.getReceiverName() : "");
        delivery.setReceiverPhone(msg.getReceiverPhone() != null ? msg.getReceiverPhone() : "");
        delivery.setSegmentType(segType);
        delivery.setBatchId(msg.getBatchId());
        delivery.setHubId(msg.getHubId());
        delivery.setRouteId(msg.getRouteId());          // 存储 routeId 供接单时直接绑定

        Long preDriver = msg.getPreAssignedDriverId();
        if (preDriver != null) {
            delivery.setDriverId(preDriver);
            delivery.setDeliveryStatus(1);
            delivery.setAcceptTime(new Date());
            log.info("[DEBUG][createLastMileDelivery] 管理员预分配司机 driverId={}, deliveryStatus=1", preDriver);
        } else {
            delivery.setDeliveryStatus(0);
            log.warn("[DEBUG][createLastMileDelivery] 无预分配司机，deliveryStatus=0（孤立记录，无人可见）");
        }

        deliveryMapper.insert(delivery);
        log.info("[DEBUG][createLastMileDelivery] 配送记录已插入，deliveryId={}, driverId={}, status={}",
                delivery.getId(), delivery.getDriverId(), delivery.getDeliveryStatus());

        if (preDriver != null) {
            // #10: 绑定物流路线（直接按 routeId 绑定）
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_ROUTE_BIND,
                new BindRouteMessage(delivery.getOrderId(), preDriver, delivery.getId(), delivery.getRouteId())
            );
            // #9: 单订单末端 → 订单状态→派送中(3)
            if (delivery.getOrderId() != null) {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ROUTING_ORDER_STATUS_UPDATE,
                    new UpdateOrderStatusMessage(delivery.getOrderId(), 3)
                );
            }
        }

        return delivery;
    }

    // ----------------------------------------------------------------
    //  管理员调度：直接为已有配送记录指派司机（直送批次末端用）
    // ----------------------------------------------------------------

    @Override
    @CacheEvict(value = "inProgressDeliveries", key = "#driverId")
    public boolean assignExistingDeliveryByRoute(Long routeId, Long driverId) {
        LambdaQueryWrapper<OrderDelivery> q = new LambdaQueryWrapper<>();
        q.eq(OrderDelivery::getRouteId, routeId)
         .eq(OrderDelivery::getDeliveryStatus, 0);
        OrderDelivery delivery = deliveryMapper.selectOne(q);
        log.info("[DEBUG][assignExistingDeliveryByRoute] routeId={}, driverId={}, 找到记录={}",
                routeId, driverId, delivery == null ? "NULL" : "id=" + delivery.getId() + ", segmentType=" + delivery.getSegmentType());
        if (delivery == null) return false;

        delivery.setDriverId(driverId);
        delivery.setDeliveryStatus(1);
        delivery.setAcceptTime(new Date());
        deliveryMapper.updateById(delivery);

        // #10: 绑定路线
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_ROUTE_BIND,
            new BindRouteMessage(delivery.getOrderId(), driverId, delivery.getId(), routeId)
        );
        // #9: 末端单订单 → 订单状态→派送中(3)
        if (delivery.getOrderId() != null) {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_ORDER_STATUS_UPDATE,
                new UpdateOrderStatusMessage(delivery.getOrderId(), 3)
            );
        }
        log.info("[Admin调度] 直送末端配送记录已指派：routeId={}, driverId={}, deliveryId={}",
                routeId, driverId, delivery.getId());
        return true;
    }

    // ----------------------------------------------------------------
    //  管理员调度：直接指派干线司机
    // ----------------------------------------------------------------

    @Override
    public OrderDelivery adminAssignTrunkDriver(Long batchId, Long driverId, Long vehicleId) {
        LambdaQueryWrapper<OrderDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDelivery::getBatchId, batchId)
               .eq(OrderDelivery::getSegmentType, 1)
               .eq(OrderDelivery::getDeliveryStatus, 0);
        OrderDelivery delivery = deliveryMapper.selectOne(wrapper);
        if (delivery == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(),
                "未找到批次 " + batchId + " 的干线待接单记录，请检查批次是否已生成干线路线");
        }

        delivery.setDriverId(driverId);
        delivery.setVehicleId(vehicleId);
        delivery.setDeliveryStatus(1);
        delivery.setAcceptTime(new Date());
        deliveryMapper.updateById(delivery);

        // #10: 绑定干线物流路线（routeId 在 delivery.routeId 中）
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_ROUTE_BIND,
            new BindRouteMessage(null, driverId, delivery.getId(), delivery.getRouteId())
        );

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
