package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.DispatchPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DispatchPoolMapper extends BaseMapper<DispatchPool> {

    /**
     * 查询待调度且有坐标信息的订单。
     * 跨城且尚未干线到达 Hub（dispatch_origin_type=0）不进入城市末端调度池，避免与全国干线阶段重复展示。
     *
     * @param destHubId 可选，收货侧全国 Hub（national_hub.id）；null 表示不限
     */
    @Select("<script>"
            + "SELECT * FROM dispatch_pool WHERE status = 0 AND end_lat IS NOT NULL AND end_lng IS NOT NULL "
            + "AND (is_cross_city = 0 OR dispatch_origin_type = 1) "
            + "<if test='destHubId != null'> AND dest_hub_id = #{destHubId} </if>"
            + " ORDER BY enter_time ASC"
            + "</script>")
    List<DispatchPool> selectPendingWithCoords(@Param("destHubId") Long destHubId);

    /** 查询所有待进入「城市末端调度」的订单（同上过滤跨城未到件） */
    @Select("<script>"
            + "SELECT * FROM dispatch_pool WHERE status = 0 "
            + "AND (is_cross_city = 0 OR dispatch_origin_type = 1) "
            + "<if test='destHubId != null'> AND dest_hub_id = #{destHubId} </if>"
            + " ORDER BY enter_time ASC"
            + "</script>")
    List<DispatchPool> selectAllPending(@Param("destHubId") Long destHubId);

    /**
     * 查询"揽收待处理"队列：跨城订单，商家已发货（order_status=2），
     * 尚未进入干线（dispatch_origin_type=0），按发货城市 Hub 过滤。
     * 与 selectAllPending 互斥：后者过滤的恰好是这类订单。
     *
     * @param originHubId 发货所在城市 Hub ID（必填）
     */
    @Select("SELECT * FROM dispatch_pool WHERE status = 0 "
            + "AND is_cross_city = 1 AND dispatch_origin_type = 0 "
            + "AND origin_hub_id = #{originHubId} "
            + "ORDER BY enter_time ASC")
    List<DispatchPool> selectCollectionQueue(@Param("originHubId") Long originHubId);

    /** 按 orderId 列表批量查询（执行调度时补全 orderItems） */
    @Select("<script>SELECT * FROM dispatch_pool WHERE order_id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<DispatchPool> selectBatchIds(@Param("ids") List<Long> ids);

    /**
     * 按指定 orderId 列表查询（有坐标的待调度订单，供精确预览使用）
     *
     * @param destHubId 可选，与列表接口一致；非空时仅保留 dest_hub_id 匹配的订单
     */
    @Select("<script>SELECT * FROM dispatch_pool WHERE status = 0 "
            + "AND (is_cross_city = 0 OR dispatch_origin_type = 1) "
            + "AND end_lat IS NOT NULL AND end_lng IS NOT NULL "
            + "<if test='destHubId != null'> AND dest_hub_id = #{destHubId} </if> "
            + "AND order_id IN "
            + "<foreach item='id' collection='orderIds' open='(' separator=',' close=')'>#{id}</foreach>"
            + " ORDER BY enter_time ASC</script>")
    List<DispatchPool> selectPendingByOrderIds(@Param("orderIds") List<Long> orderIds,
                                               @Param("destHubId") Long destHubId);
}
