package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.LogisticsHub;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 物流中转站 Mapper
 */
@Mapper
public interface LogisticsHubMapper extends BaseMapper<LogisticsHub> {

    /**
     * 按 Haversine 球面距离查询最近的正常状态中转站
     *
     * @param lat    参考点纬度
     * @param lng    参考点经度
     * @param limit  返回数量
     * @return 按距离升序排列的 Hub 列表
     */
    @Select("""
            SELECT *,
                   (6371000 * 2 * ASIN(SQRT(
                       POWER(SIN(RADIANS(latitude  - #{lat}) / 2), 2) +
                       COS(RADIANS(#{lat})) * COS(RADIANS(latitude)) *
                       POWER(SIN(RADIANS(longitude - #{lng}) / 2), 2)
                   ))) AS distance_m
            FROM logistics_hub
            WHERE status = 0
            ORDER BY distance_m ASC
            LIMIT #{limit}
            """)
    List<LogisticsHub> selectNearestHubs(@Param("lat") double lat,
                                         @Param("lng") double lng,
                                         @Param("limit") int limit);
}
