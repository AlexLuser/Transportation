package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.InterCityBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface InterCityBatchMapper extends BaseMapper<InterCityBatch> {

    @Select("SELECT * FROM inter_city_batch WHERE flow_plan_id = #{planId} ORDER BY id")
    List<InterCityBatch> selectByPlanId(@Param("planId") Long planId);

    /**
     * 查询所有活跃批次（含预规划下游批次 CHAINED）。
     * 排序：运输中(DEPARTED) > 待发车(CREATED) > 预规划(CHAINED) > 其余。
     */
    @Select("SELECT * FROM inter_city_batch " +
            "WHERE status IN ('CHAINED','CREATED','DEPARTED') " +
            "ORDER BY FIELD(status,'DEPARTED','CREATED','CHAINED'), id ASC")
    List<InterCityBatch> selectActiveBatches();

    @Update("UPDATE logistics_route SET route_status = #{status} WHERE inter_city_batch_id = #{batchId}")
    void updateRouteStatusByBatchId(@Param("batchId") Long batchId, @Param("status") int status);

    /**
     * 查找预规划的下游批次（CHAINED 状态）。
     * 用于中转到达后自动衔接：根据同一 flow_plan 中规划好的下一跳边查找批次。
     */
    @Select("SELECT * FROM inter_city_batch " +
            "WHERE from_hub_id = #{fromHubId} AND to_hub_id = #{toHubId} " +
            "  AND flow_plan_id = #{planId} AND status = 'CHAINED' " +
            "ORDER BY id DESC LIMIT 1")
    InterCityBatch selectChainedBatch(
            @Param("fromHubId") Long fromHubId,
            @Param("toHubId") Long toHubId,
            @Param("planId") Long planId);

    /**
     * 当前在各 Hub 等待发车的货量（status=CREATED，即已分配但未出发）。
     * 返回：hubId -> 货量合计（用于计算实时负载率）。
     */
    @Select("SELECT from_hub_id AS hubId, COALESCE(SUM(item_count), 0) AS total " +
            "FROM inter_city_batch WHERE status = 'CREATED' " +
            "GROUP BY from_hub_id")
    List<Map<String, Object>> selectCurrentPendingByHub();

    /**
     * 今日各 Hub 经过的货物总量（含 CREATED/DEPARTED/ARRIVED，排除纯预规划 CHAINED）。
     * 用于计算今日预计最大负载率。
     */
    @Select("SELECT from_hub_id AS hubId, COALESCE(SUM(item_count), 0) AS total " +
            "FROM inter_city_batch " +
            "WHERE status NOT IN ('CHAINED') AND DATE(create_time) = CURDATE() " +
            "GROUP BY from_hub_id")
    List<Map<String, Object>> selectTodayTotalByHub();
}
