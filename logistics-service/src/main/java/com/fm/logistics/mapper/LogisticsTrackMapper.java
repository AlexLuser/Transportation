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
     * 获取路线最新的 N 条轨迹（用于实时展示近期轨迹）
     */
    @Select("SELECT * FROM logistics_track WHERE route_id = #{routeId} " +
            "ORDER BY track_time DESC LIMIT #{limit}")
    List<LogisticsTrack> selectLatestTracks(@Param("routeId") Long routeId,
                                             @Param("limit") Integer limit);

    /**
     * 获取路线轨迹总数（用于判断是否触发 LLM ETA 重算）
     */
    @Select("SELECT COUNT(*) FROM logistics_track WHERE route_id = #{routeId}")
    long countByRouteId(@Param("routeId") Long routeId);

    /**
     * 统计路线最近 N 条轨迹的平均速度（m/s 或 km/h，需客户端上报 speed 字段）
     */
    @Select("SELECT AVG(speed) AS avg_speed, MIN(speed) AS min_speed, MAX(speed) AS max_speed " +
            "FROM (SELECT speed FROM logistics_track " +
            "      WHERE route_id = #{routeId} AND speed IS NOT NULL AND speed > 0 " +
            "      ORDER BY track_time DESC LIMIT #{limit}) AS recent")
    Map<String, Object> selectSpeedStats(@Param("routeId") Long routeId,
                                          @Param("limit") Integer limit);
}

