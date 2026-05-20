package com.fm.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * MCMF 计算结果
 *
 * 新增多商品字段：
 *  - {@link #odRoutes}：每个 OD 对的完整路径列表（含每条路径的流量）。
 *    由 {@code McmfService.computeMultiCommodityFlow} 填充；
 *    单商品 {@code computeMinCostFlow} 返回时该字段为 null。
 */
@Data
public class McmfResultDTO {

    /** 是否成功满足所有需求 */
    private boolean feasible;

    /** 总流量（件数） */
    private int totalFlow;

    /** 总最优费用（元） */
    private BigDecimal totalCost;

    /** 各边分配的流量明细（与前端 MCMF 显示一一对应，单/多商品均填充） */
    private List<EdgeFlow> edgeFlows;

    /**
     * 多商品 MCMF 路由结果：每个 OD 对的路径分配。
     * 仅 {@code computeMultiCommodityFlow} 时填充，用于 FlowPlanService 直接进行订单批次分配，
     * 取代原来的 BFS / Dijkstra 工事代码。
     */
    private List<OdRouteResult> odRoutes;

    // ── 边流量明细（单/多商品共用） ────────────────────────────────────

    @Data
    public static class EdgeFlow {
        private Long linkId;
        private Long fromHubId;
        private Long toHubId;
        private int flowAmount;
        private BigDecimal edgeCost;
        private BigDecimal totalCost;
    }

    // ── 多商品专用数据类 ──────────────────────────────────────────────

    /**
     * OD 对标识（origin_hub_id, dest_hub_id）。
     * 作为 Map key 使用，需要 equals/hashCode。
     */
    @Data
    public static class OdPair {
        private final Long originHubId;
        private final Long destHubId;

        public OdPair(Long originHubId, Long destHubId) {
            this.originHubId = originHubId;
            this.destHubId   = destHubId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OdPair)) return false;
            OdPair that = (OdPair) o;
            return Objects.equals(originHubId, that.originHubId)
                && Objects.equals(destHubId,   that.destHubId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(originHubId, destHubId);
        }

        @Override
        public String toString() {
            return originHubId + "→" + destHubId;
        }
    }

    /**
     * 某 OD 对的全部路由方案（一个 OD 对可能对应多条路径，各路径独立承载部分流量）。
     */
    @Data
    public static class OdRouteResult {
        private Long originHubId;
        private Long destHubId;
        /**
         * 路径列表；通常为 1 条（容量充足时），容量不足时可能 2+ 条。
         * 每条路径覆盖 {@code flow} 单位的需求。
         */
        private List<PathFlow> paths;
    }

    /**
     * 单条路径 + 该路径承载的流量。
     * hubPath = [originHub, transitHub1, ..., destHub]（至少 2 个节点）。
     */
    @Data
    public static class PathFlow {
        /** Hub ID 链路，从 origin 到 dest 的完整节点序列 */
        private List<Long> hubPath;
        /** 该路径上的流量（件数） */
        private int flow;

        public PathFlow(List<Long> hubPath, int flow) {
            this.hubPath = hubPath;
            this.flow    = flow;
        }

        /** 获取第一跳目标 Hub（origin 的下一节点）；路径不足 2 节点时返回 null */
        public Long firstHop() {
            return (hubPath != null && hubPath.size() >= 2) ? hubPath.get(1) : null;
        }
    }
}
