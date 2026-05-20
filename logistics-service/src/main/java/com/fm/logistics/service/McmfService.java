package com.fm.logistics.service;

import com.fm.logistics.dto.McmfResultDTO;
import com.fm.logistics.entity.HubLink;
import com.fm.logistics.entity.NationalHub;

import java.util.List;
import java.util.Map;

/**
 * 最小费用最大流（MCMF）服务接口
 *
 * 提供两种模式：
 * <ol>
 *   <li>{@link #computeMinCostFlow}：单商品 SSP+SPFA（兼容旧调用，存在同 Hub 供需自消问题）。</li>
 *   <li>{@link #computeMultiCommodityFlow}：多商品 Dijkstra-SSP，每个 OD 对独立寻路，
 *       共享边容量，无供需自消；返回结果含各 OD 对完整路径，可直接用于订单批次分配。</li>
 * </ol>
 */
public interface McmfService {

    /**
     * 单商品 MCMF（保留兼容）。
     * 以聚合 supply/demand 建图，存在同 Hub 供需自消缺陷。
     */
    McmfResultDTO computeMinCostFlow(
            List<NationalHub> hubs,
            List<HubLink> links,
            Map<Long, Integer> supplyByHub,
            Map<Long, Integer> demandByHub
    );

    /**
     * 多商品 MCMF（推荐）。
     *
     * <p>每个 OD 对作为一种独立商品，Dijkstra-SSP 在共享残差容量图上逐商品寻路。
     * 不存在单商品建模的「同 Hub 供需自消」问题：
     * 即便某 Hub 同时是 A→X 的目的地和 X→B 的起点，两个商品各自寻路，互不干扰。</p>
     *
     * <p>处理顺序：按需求量降序（高需求 OD 对优先占用容量）。
     * 若某 OD 对容量耗尽（路径不可达），会记录警告并继续处理其余商品，不终止整体规划。</p>
     *
     * @param hubs      网络节点列表（活跃 Hub）
     * @param links     网络边列表（含今日有效费用 costPerUnit，容量 capacityDaily）
     * @param odDemands 各 OD 对的需求件数（key = (originHubId, destHubId)，value = 订单数）
     * @return 结果含：总边流明细（edgeFlows，用于前端展示）+
     *                  各 OD 对路由方案（odRoutes，用于订单批次分配）
     */
    McmfResultDTO computeMultiCommodityFlow(
            List<NationalHub> hubs,
            List<HubLink> links,
            Map<McmfResultDTO.OdPair, Integer> odDemands
    );
}
