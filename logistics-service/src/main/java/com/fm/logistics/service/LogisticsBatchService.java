package com.fm.logistics.service;

import com.fm.logistics.dto.BatchDetailDTO;
import com.fm.logistics.dto.CreateBatchRequestDTO;

/**
 * 配送批次服务接口
 */
public interface LogisticsBatchService {

    /**
     * 创建配送批次（Hub-and-Spoke 核心流程）
     *
     * 执行步骤：
     *   1. 调 VrpService 计算最优访问顺序
     *   2. 调 LogisticsHubService 选最近 Hub（useHub=true 时）
     *   3. 写 logistics_batch 记录
     *   4. 创建干线路线（仓库→Hub，segment_type=1）
     *   5. 为每个订单创建末端路线（Hub→客户，segment_type=2，初始状态=0待激活）
     *   6. 写 logistics_batch_item（含 visitSequence）
     *
     * @param requestDTO 批次创建请求
     * @return 批次详情（含干线路线+末端路线列表）
     */
    BatchDetailDTO createBatch(CreateBatchRequestDTO requestDTO);

    /**
     * 获取批次详情
     */
    BatchDetailDTO getBatchDetail(Long batchId);

    /**
     * 干线司机到达 Hub 后，激活批次内所有末端路线
     *
     * 执行步骤：
     *   1. 更新 logistics_batch.batch_status → 2（已到中转站）
     *   2. 将所有末端路线（segment_type=2）状态改为0（待出发）
     *   3. 为每条末端路线发 LastMileActivateMessage（通知 driver-service 创建配送单）
     *
     * @param batchId 批次ID
     */
    void activateLastMileRoutes(Long batchId);

    /**
     * 更新批次状态（内部调用，检查末端路线完成情况）
     */
    void checkAndUpdateBatchStatus(Long batchId);

    /**
     * 跨城干线到达目标城市后，触发目标城市末端 VRP 规划
     * （与 createBatch 逻辑相同，但起点为 destHub 而非仓库）
     *
     * @param interCityBatchId 跨城干线批次ID
     * @param destHubId        目标城市Hub ID
     */
    void activateFromInterCityArrival(Long interCityBatchId, Long destHubId);
}
