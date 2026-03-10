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
     * 统计目标区域的历史配送数据（用于 LLM Prompt 构建）
     * 匹配规则：end_address 包含相同城市关键词，且已完成（status=2）
     *
     * 返回字段：
     *   - sample_count      : 样本数量
     *   - avg_duration_min  : 平均配送时长（分钟）
     *   - max_duration_min  : 最大时长
     *   - min_duration_min  : 最小时长
     *   - delay_count       : 超出预计时间 15 分钟以上的次数
     */
    @Select("SELECT " +
            "  COUNT(*) AS sample_count, " +
            "  AVG(TIMESTAMPDIFF(MINUTE, create_time, actual_arrival_time)) AS avg_duration_min, " +
            "  MAX(TIMESTAMPDIFF(MINUTE, create_time, actual_arrival_time)) AS max_duration_min, " +
            "  MIN(TIMESTAMPDIFF(MINUTE, create_time, actual_arrival_time)) AS min_duration_min, " +
            "  SUM(CASE WHEN actual_arrival_time > DATE_ADD(estimated_arrival_time, INTERVAL 15 MINUTE) THEN 1 ELSE 0 END) AS delay_count " +
            "FROM logistics_route " +
            "WHERE route_status = 2 " +
            "  AND end_address LIKE CONCAT('%', #{cityKeyword}, '%') " +
            "  AND actual_arrival_time IS NOT NULL")
    Map<String, Object> selectHistoricalStats(@Param("cityKeyword") String cityKeyword);

    /**
     * 获取最近 30 条同目标区域的完成路线（含时长），用于时间段分布分析
     */
    @Select("SELECT " +
            "  HOUR(create_time) AS depart_hour, " +
            "  TIMESTAMPDIFF(MINUTE, create_time, actual_arrival_time) AS duration_min " +
            "FROM logistics_route " +
            "WHERE route_status = 2 " +
            "  AND end_address LIKE CONCAT('%', #{cityKeyword}, '%') " +
            "  AND actual_arrival_time IS NOT NULL " +
            "ORDER BY create_time DESC " +
            "LIMIT 30")
    List<Map<String, Object>> selectRecentDurations(@Param("cityKeyword") String cityKeyword);
}

