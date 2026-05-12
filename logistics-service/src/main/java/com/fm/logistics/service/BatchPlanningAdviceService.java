package com.fm.logistics.service;

import com.fm.logistics.dto.BatchAdviceDTO;
import com.fm.logistics.dto.CreateBatchRequestDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批次配送策略顾问服务（方案A）
 *
 * 在创建批次之前，调用此接口让 LLM 分析：
 *   - 订单地理分布是否适合 Hub-and-Spoke 模式
 *   - 合并配送 vs 直送的里程对比
 *   - 推荐的配送策略及理由
 *
 * 接口路径：POST /api/logistics/batches/advise
 */
public interface BatchPlanningAdviceService {

    /**
     * 获取批次配送策略建议
     *
     * @param warehouseLat     仓库纬度
     * @param warehouseLng     仓库经度
     * @param warehouseAddress 仓库地址（供 LLM 描述使用）
     * @param orderItems       订单目的地列表（含坐标和地址）
     * @param plannedTime      计划发车时间（null 则取当前时间）
     * @return 配送策略建议（含 LLM 理由、里程估算、是否推荐 Hub 模式）
     */
    BatchAdviceDTO advise(double warehouseLat,
                          double warehouseLng,
                          String warehouseAddress,
                          List<CreateBatchRequestDTO.OrderItem> orderItems,
                          LocalDateTime plannedTime);
}
