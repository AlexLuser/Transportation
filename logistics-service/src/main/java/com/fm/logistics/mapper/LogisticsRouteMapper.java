package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.LogisticsRoute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LogisticsRouteMapper extends BaseMapper<LogisticsRoute> {

    /**
     * 按出发小时统计过去30天的平均延误分钟数
     * 延误 = actual_arrival_time - estimated_arrival_time（分钟，正数=晚到，负数=早到）
     * 用途：LLM历史数据上下文——识别各时段延误规律
     * 返回字段：hourBucket（小时0-23）、avgDelayMin（平均延误分钟）
     */
    @Select("SELECT HOUR(create_time) AS hourBucket, " +
            "AVG(TIMESTAMPDIFF(MINUTE, estimated_arrival_time, actual_arrival_time)) AS avgDelayMin " +
            "FROM logistics_route " +
            "WHERE actual_arrival_time IS NOT NULL " +
            "AND estimated_arrival_time IS NOT NULL " +
            "AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY HOUR(create_time) " +
            "ORDER BY hourBucket")
    List<Map<String, Object>> selectDelayStatsByHour();

    /**
     * 统计近7天起点附近（约2km范围）的异常路线数量
     * 异常路线：route_status = 3（配送中断/超时异常）
     * 用途：LLM历史数据上下文——评估起点区域安全性
     * 返回字段：exceptionCount（异常次数）、lastExceptionTime（最近异常时间）
     */
    @Select("SELECT COUNT(*) AS exceptionCount, MAX(create_time) AS lastExceptionTime " +
            "FROM logistics_route " +
            "WHERE route_status = 3 " +
            "AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "AND ABS(start_lat - #{startLat}) < 0.02 " +
            "AND ABS(start_lng - #{startLon}) < 0.02")
    Map<String, Object> selectExceptionStats(@Param("startLat") double startLat,
                                             @Param("startLon") double startLon);
}

