package com.fm.logistics.service;

import com.fm.logistics.dto.ClusterResultDTO;
import com.fm.logistics.dto.DispatchPoolItemDTO;
import com.fm.logistics.dto.GlobalDispatchAdviceDTO;

import java.util.List;

/**
 * LLM 全局调度顾问 — 对 K-Means 聚类结果进行宏观调度策略分析
 */
public interface GlobalDispatchAdviceService {

    /**
     * 分析聚类结果并给出全局调度建议
     *
     * @param clusters      K-Means 聚类结果
     * @param warehouseId   主仓库ID（保留，当前由前端直接传坐标代替）
     * @param warehouseLat  仓库纬度（用于计算仓库→簇距离，可为 null）
     * @param warehouseLng  仓库经度（可为 null）
     * @return 全局调度建议
     */
    GlobalDispatchAdviceDTO advise(List<ClusterResultDTO> clusters, Long warehouseId,
                                   Double warehouseLat, Double warehouseLng);

    /**
     * 对调度池订单逐条检测备注是否含紧急配送需求（由 LLM 判断）
     *
     * @param items 含备注的待调度订单列表
     * @return 被 LLM 判定为紧急的订单 ID 列表
     */
    List<Long> checkUrgency(List<DispatchPoolItemDTO> items);
}
