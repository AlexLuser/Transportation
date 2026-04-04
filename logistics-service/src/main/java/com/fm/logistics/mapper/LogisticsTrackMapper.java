package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.LogisticsTrack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LogisticsTrackMapper extends BaseMapper<LogisticsTrack> {

    /**
     * 查最近 N 条轨迹（时间倒序）
     * 用途：买家查看运输员近期位置轨迹段
     */
    @Select("SELECT * FROM logistics_track " +
            "WHERE route_id = #{routeId} " +
            "ORDER BY track_time DESC " +
            "LIMIT #{limit}")
    List<LogisticsTrack> selectLatestTracks(@Param("routeId") Long routeId,
                                            @Param("limit") Integer limit);

    /**
     * 查该路线全部轨迹（时间正序）
     * 用途：轨迹回放，按时间顺序还原行驶路径
     */
    @Select("SELECT * FROM logistics_track " +
            "WHERE route_id = #{routeId} " +
            "ORDER BY track_time ASC")
    List<LogisticsTrack> selectAllByRouteId(@Param("routeId") Long routeId);

    /**
     * 查最新一条轨迹（当前位置）
     * 用途：买家实时查看运输员当前在哪
     */
    @Select("SELECT * FROM logistics_track " +
            "WHERE route_id = #{routeId} " +
            "ORDER BY track_time DESC " +
            "LIMIT 1")
    LogisticsTrack selectLatestOne(@Param("routeId") Long routeId);

    /**
     * 按小时统计过去30天的平均行驶速度
     * 用途：LLM历史数据上下文——识别各时段行驶速度基线
     * 返回字段：hourBucket（小时0-23）、avgSpeed（km/h）、sampleCount（样本量）
     */
    @Select("SELECT HOUR(track_time) AS hourBucket, " +
            "AVG(speed) AS avgSpeed, " +
            "COUNT(*) AS sampleCount " +
            "FROM logistics_track " +
            "WHERE speed IS NOT NULL AND speed > 0 " +
            "AND track_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY HOUR(track_time) " +
            "ORDER BY hourBucket")
    List<Map<String, Object>> selectAvgSpeedByHour();

    /**
     * 查询指定区域内过去7天的历史慢速热点（均速 < 8km/h，出现 ≥ 3次）
     * 用途：LLM历史数据上下文——识别拥堵高发区域，辅助方案B路点决策
     * 返回字段：latBucket、lonBucket、avgSpeed（km/h）、occurrences（次数）、peakHour（高峰小时）
     */
    @Select("SELECT ROUND(latitude, 2) AS latBucket, " +
            "ROUND(longitude, 2) AS lonBucket, " +
            "AVG(speed) AS avgSpeed, " +
            "COUNT(*) AS occurrences, " +
            "HOUR(MAX(track_time)) AS peakHour " +
            "FROM logistics_track " +
            "WHERE speed IS NOT NULL AND speed > 0 " +
            "AND track_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "AND latitude  BETWEEN #{minLat} AND #{maxLat} " +
            "AND longitude BETWEEN #{minLon} AND #{maxLon} " +
            "GROUP BY ROUND(latitude, 2), ROUND(longitude, 2) " +
            "HAVING AVG(speed) < 8 AND COUNT(*) >= 3 " +
            "ORDER BY AVG(speed) ASC " +
            "LIMIT 5")
    List<Map<String, Object>> selectSlowZones(@Param("minLat") double minLat,
                                              @Param("maxLat") double maxLat,
                                              @Param("minLon") double minLon,
                                              @Param("maxLon") double maxLon);
}
