package com.fm.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用于 logistics-service 查询 order_info 中的 Hub 供需情况
 */
@Mapper
public interface OrderInfoMapper {

    /**
     * 按 origin_hub_id 统计当前可用供给量（未绑定批次的跨城待规划单）。
     * 包含：
     *   ① order_status=2（待揽件）：当日新建订单；
     *   ② order_status=3（派送中）且 flow_plan_id IS NOT NULL：经过中转 Hub 后仍在途等待下一段的订单
     *      （中转到达后保持 status=3 不回退，以避免状态倒退、保证可追溯性）。
     */
    @Select("SELECT origin_hub_id AS hubId, COUNT(*) AS cnt " +
            "FROM order_info " +
            "WHERE order_status IN (2, 3) " +
            "  AND origin_hub_id IS NOT NULL " +
            "  AND dest_hub_id IS NOT NULL " +
            "  AND origin_hub_id != dest_hub_id " +
            "  AND inter_city_batch_id IS NULL " +
            "  AND (DATE(create_time) = #{planDate} OR flow_plan_id IS NOT NULL) " +
            "GROUP BY origin_hub_id")
    List<Map<String, Object>> selectSupplyByHub(@Param("planDate") LocalDate planDate);

    /**
     * 按 dest_hub_id 统计当前待收货需求量。
     * 包含同 selectSupplyByHub 的两类订单。
     */
    @Select("SELECT dest_hub_id AS hubId, COUNT(*) AS cnt " +
            "FROM order_info " +
            "WHERE order_status IN (2, 3) " +
            "  AND origin_hub_id IS NOT NULL " +
            "  AND dest_hub_id IS NOT NULL " +
            "  AND origin_hub_id != dest_hub_id " +
            "  AND inter_city_batch_id IS NULL " +
            "  AND (DATE(create_time) = #{planDate} OR flow_plan_id IS NOT NULL) " +
            "GROUP BY dest_hub_id")
    List<Map<String, Object>> selectDemandByHub(@Param("planDate") LocalDate planDate);

    /** 查询归属指定跨城批次的订单ID列表 */
    @Select("SELECT id FROM order_info WHERE inter_city_batch_id = #{batchId}")
    List<Long> selectOrderIdsByInterCityBatchId(@Param("batchId") Long batchId);

    @Select("SELECT id AS id, order_no AS orderNo, order_status AS orderStatus, " +
            "origin_hub_id AS originHubId, dest_hub_id AS destHubId " +
            "FROM order_info WHERE inter_city_batch_id = #{batchId} ORDER BY id ASC")
    List<Map<String, Object>> selectOrderRowsByInterCityBatchId(@Param("batchId") Long batchId);

    /** 批量更新归属指定跨城批次的订单状态 */
    @Update("UPDATE order_info SET order_status = #{status} WHERE inter_city_batch_id = #{batchId}")
    void updateStatusByInterCityBatchId(@Param("batchId") Long batchId, @Param("status") int status);

    /**
     * 按 (originHub, destHub) 统计各 OD 对的待规划订单数。
     * 用于多商品 MCMF 建图时确定每个商品的需求量。
     * 与 {@link #selectSupplyByHub} / {@link #selectDemandByHub} 使用相同过滤条件，
     * 但按 OD 对分组，不做任何聚合/净额，确保每个 (origin, dest) 独立成商品。
     */
    @Select("SELECT origin_hub_id AS originHubId, dest_hub_id AS destHubId, COUNT(*) AS cnt " +
            "FROM order_info o " +
            "WHERE o.order_status IN (2, 3) " +
            "  AND o.origin_hub_id IS NOT NULL " +
            "  AND o.dest_hub_id IS NOT NULL " +
            "  AND o.origin_hub_id != o.dest_hub_id " +
            "  AND o.inter_city_batch_id IS NULL " +
            "  AND (DATE(o.create_time) = #{planDate} OR o.flow_plan_id IS NOT NULL) " +
            "  AND EXISTS (SELECT 1 FROM hub_sorting_record sr " +
            "              WHERE sr.order_id = o.id AND sr.sort_result = 'PENDING') " +
            "GROUP BY o.origin_hub_id, o.dest_hub_id " +
            "ORDER BY cnt DESC")
    List<Map<String, Object>> selectOdPairDemands(@Param("planDate") LocalDate planDate);

    /**
     * 查询所有待规划的跨城订单，用于 MCMF 路径绑定。
     * 包含：
     *   ① order_status=2：当日新建待揽件订单；
     *   ② order_status=3 且 flow_plan_id IS NOT NULL：经中转 Hub 后保持「派送中」等待下段的在途订单。
     */
    @Select("SELECT o.id AS id, o.origin_hub_id AS originHubId, o.dest_hub_id AS destHubId " +
            "FROM order_info o " +
            "WHERE o.order_status IN (2, 3) " +
            "AND o.origin_hub_id IS NOT NULL AND o.dest_hub_id IS NOT NULL " +
            "AND o.origin_hub_id != o.dest_hub_id " +
            "AND o.inter_city_batch_id IS NULL " +
            "AND (DATE(o.create_time) = #{planDate} OR o.flow_plan_id IS NOT NULL) " +
            "AND EXISTS (SELECT 1 FROM hub_sorting_record sr " +
            "            WHERE sr.order_id = o.id AND sr.sort_result = 'PENDING') " +
            "ORDER BY o.id ASC")
    List<Map<String, Object>> selectUnassignedCrossCityOrders(@Param("planDate") LocalDate planDate);

    @Select("SELECT COUNT(*) FROM order_info WHERE inter_city_batch_id = #{batchId}")
    int countByInterCityBatchId(@Param("batchId") Long batchId);

    /**
     * 将订单挂到干线批次（与 ShipmentRouting 使用同一套 national_hub id）
     */
    @Update("<script>" +
            "UPDATE order_info SET inter_city_batch_id = #{batchId}, flow_plan_id = #{planId} " +
            "WHERE inter_city_batch_id IS NULL AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int assignOrdersToInterCityBatch(
            @Param("ids") List<Long> ids,
            @Param("batchId") Long batchId,
            @Param("planId") Long planId);

    /**
     * 综合供需：正值=供给(origin)，负值=需求(dest)
     * @deprecated 仅兼容旧调用；MCMF 建图应使用 {@link #grossSupplyByHub} 与 {@link #grossDemandByHub}，避免同一 Hub 进出代数和抵消。
     */
    @Deprecated
    default Map<Long, Integer> calcSupplyDemand(LocalDate planDate) {
        Map<Long, Integer> result = new HashMap<>();
        selectSupplyByHub(planDate).forEach(row -> {
            Long hubId = ((Number) row.get("hubId")).longValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            result.merge(hubId, cnt, Integer::sum);
        });
        selectDemandByHub(planDate).forEach(row -> {
            Long hubId = ((Number) row.get("hubId")).longValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            result.merge(hubId, -cnt, Integer::sum);
        });
        return result;
    }

    /** 各 Hub 当日跨城发货量（不做净额） */
    default Map<Long, Integer> grossSupplyByHub(LocalDate planDate) {
        Map<Long, Integer> m = new HashMap<>();
        selectSupplyByHub(planDate).forEach(row -> {
            Long hubId = ((Number) row.get("hubId")).longValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            m.merge(hubId, cnt, Integer::sum);
        });
        return m;
    }

    /** 各 Hub 当日跨城收货量（不做净额） */
    default Map<Long, Integer> grossDemandByHub(LocalDate planDate) {
        Map<Long, Integer> m = new HashMap<>();
        selectDemandByHub(planDate).forEach(row -> {
            Long hubId = ((Number) row.get("hubId")).longValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            m.merge(hubId, cnt, Integer::sum);
        });
        return m;
    }

    /**
     * 归属某干线批次、且最终目的 Hub 等于「本段到达 Hub」的订单（仅此类可在该 Hub 发末端/入城配池）。
     */
    @Select("SELECT id FROM order_info WHERE inter_city_batch_id = #{batchId} AND dest_hub_id = #{destHubId}")
    List<Long> selectOrderIdsByBatchAndFinalDestHub(
            @Param("batchId") Long batchId,
            @Param("destHubId") Long destHubId);

    /**
     * 中转到达后重置订单：清除批次绑定，将当前 Hub 设为新的 origin_hub。
     * <p>
     * 关键：<b>不修改 order_status</b>，订单保持 status=3（派送中），避免状态倒退和物流不可追溯。
     * 保留 flow_plan_id，下次 MCMF 规划时通过「order_status IN (2,3) AND flow_plan_id IS NOT NULL」
     * 识别为需要继续安排下段的在途中转订单。
     */
    @Update("<script>" +
            "UPDATE order_info SET inter_city_batch_id = NULL, origin_hub_id = #{currentHubId} " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int resetTransitOrdersAfterHop(@Param("ids") List<Long> ids, @Param("currentHubId") Long currentHubId);

    /**
     * 批量写入订单的完整规划路径（用于多跳自动衔接）。
     * path 格式：JSON 数组字符串，如 "[1,3,7]"。
     */
    @Update("<script>" +
            "UPDATE order_info SET planned_path = #{path} WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    void updateOrdersPlannedPath(@Param("ids") List<Long> ids, @Param("path") String path);

    /**
     * 查询单个订单的完整规划路径（JSON 字符串，如 "[1,3,7]"）。
     */
    @Select("SELECT planned_path FROM order_info WHERE id = #{orderId}")
    String selectPlannedPathById(@Param("orderId") Long orderId);

    /**
     * 将单个中转订单衔接到下一跳批次：更新批次绑定 + 当前 Hub 设为新起点。
     * 不修改 order_status，保持 status=3（派送中）。
     */
    @Update("UPDATE order_info SET inter_city_batch_id = #{nextBatchId}, origin_hub_id = #{currentHubId} " +
            "WHERE id = #{orderId}")
    void assignOrderToNextBatch(
            @Param("orderId") Long orderId,
            @Param("nextBatchId") Long nextBatchId,
            @Param("currentHubId") Long currentHubId);

    /**
     * 批量将中转订单衔接到下一跳批次（同一跳的所有订单一次性更新，避免循环内重复激活批次状态导致后续订单查不到 CHAINED 批次）。
     */
    @Update("<script>" +
            "UPDATE order_info SET inter_city_batch_id = #{nextBatchId}, origin_hub_id = #{currentHubId} " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int assignOrdersToNextBatch(
            @Param("ids") List<Long> ids,
            @Param("nextBatchId") Long nextBatchId,
            @Param("currentHubId") Long currentHubId);

    /**
     * 统计各 Origin-Hub 当前所有跨城活跃订单数（不论是否已绑定批次）。
     * 用于计算"今日预计最大负载率"——从订单产生起即可展示，不依赖 MCMF 批次是否已创建。
     * 包含：order_status=2（待揽件）、3（派送中）的跨城订单。
     */
    @Select("SELECT origin_hub_id AS hubId, COUNT(*) AS total " +
            "FROM order_info " +
            "WHERE origin_hub_id IS NOT NULL " +
            "  AND dest_hub_id IS NOT NULL " +
            "  AND origin_hub_id != dest_hub_id " +
            "  AND order_status IN (2, 3) " +
            "GROUP BY origin_hub_id")
    List<Map<String, Object>> selectActiveCrossCityCountByOriginHub();

    /** 为 dispatch_pool 补单行：订单 + 收货地址坐标 */
    @Select("SELECT o.id AS orderId, o.shop_id AS shopId, o.warehouse_id AS warehouseId, "
            + "o.origin_hub_id AS originHubId, o.dest_hub_id AS destHubId, o.remark AS remark, "
            + "TRIM(CONCAT(IFNULL(ca.province,''), IFNULL(ca.city,''), IFNULL(ca.district,''), IFNULL(ca.detail_address,''))) AS endAddress, "
            + "ca.latitude AS endLat, ca.longitude AS endLng, "
            + "IFNULL(ca.receiver_name,'') AS receiverName, IFNULL(ca.receiver_phone,'') AS receiverPhone "
            + "FROM order_info o LEFT JOIN customer_address ca ON o.address_id = ca.id WHERE o.id = #{orderId}")
    Map<String, Object> selectOrderRowForDispatchPool(@Param("orderId") long orderId);
}
