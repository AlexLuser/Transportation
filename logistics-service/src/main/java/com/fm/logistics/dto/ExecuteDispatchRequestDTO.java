package com.fm.logistics.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行调度请求 DTO — 管理员在预览确认后调用执行接口
 */
@Data
public class ExecuteDispatchRequestDTO {

    /**
     * 每个批次的创建参数列表（由前端基于预览结果填写）
     */
    private List<BatchItem> batches;

    @Data
    public static class BatchItem {
        /** 该批次内的订单ID列表 */
        private List<Long> orderIds;

        /** 发货仓库ID */
        private Long warehouseId;

        /** 仓库纬度 */
        private Double warehouseLat;

        /** 仓库经度 */
        private Double warehouseLng;

        /** 仓库地址 */
        private String warehouseAddress;

        /** 是否走 Hub */
        private Boolean useHub;

        /**
         * 是否为跨城干线到达后的末端配送批次（true=强制直送，不经本地分拨 Hub）。
         * 前端对含 dispatchOriginType=1 的集群设为 true。
         */
        private Boolean isCrossCity;

        /** 指定 Hub ID（可选，null=自动选） */
        private Long hubId;

        /** 计划发货时间（可选） */
        private LocalDateTime plannedShipTime;

        /** 订单目的地信息（从 dispatch_pool 查出填写） */
        private List<CreateBatchRequestDTO.OrderItem> orderItems;
    }
}
