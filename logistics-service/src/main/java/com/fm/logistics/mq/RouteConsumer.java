package com.fm.logistics.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fm.logistics.config.RabbitMQConfig;
import com.fm.logistics.dto.RouteDetailDTO;
import com.fm.logistics.entity.DispatchPool;
import com.fm.logistics.mapper.DispatchPoolMapper;
import com.fm.logistics.service.LogisticsBatchService;
import com.fm.logistics.service.LogisticsRouteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * logistics-service MQ 消费者
 *
 * 消费场景：
 *   #10 绑定路线（driver-service 接单后发送：将 driverId/deliveryId 写入路线）
 *   #11 更新路线状态（driver-service 配送状态变更时发送）
 *   #13 Hub到达通知（driver-service 干线到Hub后发送：激活末端路线）
 *
 * 确认机制：AUTO ack + retry(3次) + 失败进死信队列
 */
@Slf4j
@Component
public class RouteConsumer {

    @Autowired
    private LogisticsRouteService logisticsRouteService;

    @Autowired
    private LogisticsBatchService logisticsBatchService;

    @Autowired
    private DispatchPoolMapper dispatchPoolMapper;

    @Autowired
    private com.fm.logistics.mapper.WarehouseMapper warehouseMapper;

    /**
     * #10 处理绑定路线消息
     * 通过 orderId 查找路线，将 driverId 和 deliveryId 写入
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ROUTE_BIND)
    public void handleRouteBind(BindRouteMessage message) {
        log.info("[MQ消费] #10 绑定路线: orderId={}, driverId={}, deliveryId={}, routeId={}",
                message.getOrderId(), message.getDriverId(), message.getDeliveryId(), message.getRouteId());
        try {
            if (message.getRouteId() != null) {
                // 智能调度模式：直接按 routeId 绑定
                logisticsRouteService.bindDriver(
                    message.getRouteId(),
                    message.getDriverId(),
                    message.getDeliveryId()
                );
                log.info("[MQ消费] #10 直接绑定路线成功: routeId={}", message.getRouteId());
            } else {
                // 传统模式：通过 orderId 查找路线再绑定
                RouteDetailDTO routeDetail = logisticsRouteService.getRouteDetailByOrderId(message.getOrderId());
                if (routeDetail == null || routeDetail.getRoute() == null) {
                    log.warn("[MQ消费] #10 路线不存在，跳过绑定: orderId={}", message.getOrderId());
                    return;
                }
                logisticsRouteService.bindDriver(
                    routeDetail.getRoute().getId(),
                    message.getDriverId(),
                    message.getDeliveryId()
                );
                log.info("[MQ消费] #10 绑定路线成功: routeId={}", routeDetail.getRoute().getId());
            }
        } catch (Exception e) {
            log.error("[MQ消费] #10 绑定路线失败: orderId={}, error={}", message.getOrderId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("绑定路线失败，进入死信队列: " + e.getMessage(), e);
        }
    }

    /**
     * #11 处理路线状态更新消息
     * 通过 orderId 查找路线，更新路线状态
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ROUTE_STATUS_UPDATE)
    public void handleRouteStatusUpdate(RouteStatusMessage message) {
        log.info("[MQ消费] #11 更新路线状态: orderId={}, routeStatus={}", message.getOrderId(), message.getRouteStatus());
        try {
            RouteDetailDTO routeDetail = logisticsRouteService.getRouteDetailByOrderId(message.getOrderId());
            if (routeDetail == null || routeDetail.getRoute() == null) {
                log.warn("[MQ消费] #11 路线不存在，跳过状态更新: orderId={}", message.getOrderId());
                return;
            }
            logisticsRouteService.updateRouteStatus(
                routeDetail.getRoute().getId(),
                message.getRouteStatus()
            );
            log.info("[MQ消费] #11 路线状态更新成功: routeId={}, status={}", routeDetail.getRoute().getId(), message.getRouteStatus());
        } catch (Exception e) {
            log.error("[MQ消费] #11 路线状态更新失败: orderId={}, error={}", message.getOrderId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("路线状态更新失败，进入死信队列: " + e.getMessage(), e);
        }
    }

    /**
     * #15 处理订单入调度池消息
     * 将 order-service 备货完成的订单写入 dispatch_pool，等待调度系统统一处理
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_DISPATCH_POOL_ADD)
    public void handleAddToPool(AddToPoolMessage message) {
        log.info("[MQ消费] #15 订单入调度池: orderId={}, warehouseId={}", message.getOrderId(), message.getWarehouseId());
        try {
            // 幂等：已存在则跳过
            Long count = dispatchPoolMapper.selectCount(
                new LambdaQueryWrapper<DispatchPool>().eq(DispatchPool::getOrderId, message.getOrderId())
            );
            if (count > 0) {
                log.info("[MQ消费] #15 订单已在调度池中，跳过: orderId={}", message.getOrderId());
                return;
            }
            DispatchPool pool = new DispatchPool();
            pool.setOrderId(message.getOrderId());
            pool.setShopId(message.getShopId());
            pool.setWarehouseId(message.getWarehouseId());
            pool.setEndAddress(message.getEndAddress());
            pool.setEndLat(message.getEndLat());
            pool.setEndLng(message.getEndLng());
            pool.setReceiverName(message.getReceiverName() != null ? message.getReceiverName() : "");
            pool.setReceiverPhone(message.getReceiverPhone() != null ? message.getReceiverPhone() : "");
            pool.setRemark(message.getRemark());
            // MCMF 扩展字段
            pool.setOriginHubId(message.getOriginHubId());
            pool.setDestHubId(message.getDestHubId());
            pool.setIsCrossCity(message.isCrossCity() ? 1 : 0);
            // 调度起点类型：优先使用消息中的值，默认为 0（仓库）
            int originType = (message.getDispatchOriginType() != null) ? message.getDispatchOriginType() : 0;
            pool.setDispatchOriginType(originType);

            if (originType == 2) {
                // 个人寄件：使用消息中的发件人地址作为调度起点
                pool.setDispatchOriginLat(message.getSenderLat());
                pool.setDispatchOriginLng(message.getSenderLng());
                pool.setDispatchOriginAddr(message.getSenderAddress());
            } else if (message.getWarehouseId() != null) {
                // 商户订单：从仓库表读取起点坐标
                com.fm.logistics.entity.Warehouse wh = warehouseMapper.selectById(message.getWarehouseId());
                if (wh != null) {
                    pool.setDispatchOriginLat(wh.getLatitude());
                    pool.setDispatchOriginLng(wh.getLongitude());
                    pool.setDispatchOriginAddr(
                        java.util.stream.Stream.of(wh.getProvince(), wh.getCity(), wh.getWarehouseName())
                            .filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.joining()));
                }
            }
            pool.setStatus(0);
            pool.setEnterTime(LocalDateTime.now());
            dispatchPoolMapper.insert(pool);
            log.info("[MQ消费] #15 订单入池成功: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("[MQ消费] #15 订单入池失败: orderId={}, error={}", message.getOrderId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("订单入池失败，进入死信队列: " + e.getMessage(), e);
        }
    }

    /**
     * #13 处理 Hub 到达通知
     * 干线司机到达中转站后，激活批次内所有末端路线并通知 driver-service 创建末端配送单
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_HUB_ARRIVAL)
    public void handleHubArrival(HubArrivalMessage message) {
        log.info("[MQ消费] #13 Hub到达通知: batchId={}, hubId={}, driverId={}",
                message.getBatchId(), message.getHubId(), message.getTrunkDriverId());
        try {
            logisticsBatchService.activateLastMileRoutes(message.getBatchId());
            log.info("[MQ消费] #13 末端路线激活完成: batchId={}", message.getBatchId());
        } catch (Exception e) {
            log.error("[MQ消费] #13 Hub到达处理失败: batchId={}, error={}", message.getBatchId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Hub到达处理失败，进入死信队列: " + e.getMessage(), e);
        }
    }
}
