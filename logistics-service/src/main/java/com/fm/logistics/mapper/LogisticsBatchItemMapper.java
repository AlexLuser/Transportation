package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.LogisticsBatchItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 批次订单明细 Mapper
 */
@Mapper
public interface LogisticsBatchItemMapper extends BaseMapper<LogisticsBatchItem> {

    /**
     * 按批次ID查询明细列表，按 VRP 访问顺序升序排列
     */
    @Select("SELECT * FROM logistics_batch_item WHERE batch_id = #{batchId} ORDER BY visit_sequence ASC")
    List<LogisticsBatchItem> selectByBatchId(@Param("batchId") Long batchId);

    /**
     * 查询批次中待激活（item_status=0）的明细
     */
    @Select("SELECT * FROM logistics_batch_item WHERE batch_id = #{batchId} AND item_status = 0 ORDER BY visit_sequence ASC")
    List<LogisticsBatchItem> selectPendingByBatchId(@Param("batchId") Long batchId);

    /**
     * 通过订单ID查询对应的批次明细（用于新调度体系下按 orderId 反查末端路线）
     */
    @Select("SELECT * FROM logistics_batch_item WHERE order_id = #{orderId} AND route_id IS NOT NULL LIMIT 1")
    LogisticsBatchItem selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 按路线ID查询该路线下所有停靠点明细（多停靠末端路线用）
     */
    @Select("SELECT * FROM logistics_batch_item WHERE route_id = #{routeId} ORDER BY stop_sequence ASC")
    List<LogisticsBatchItem> selectByRouteId(@Param("routeId") Long routeId);

    /**
     * 查询某路线下未送达（item_status != 2）的停靠点数量
     */
    @Select("SELECT COUNT(*) FROM logistics_batch_item WHERE route_id = #{routeId} AND item_status != 2")
    int countPendingByRouteId(@Param("routeId") Long routeId);
}
