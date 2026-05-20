package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.NationalHub;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NationalHubMapper extends BaseMapper<NationalHub> {

    /**
     * 查询距离指定坐标最近的城市级配送中心（hub_level=2）
     * 使用 Haversine 公式近似计算距离
     */
    @Select("SELECT *, " +
            "  (6371 * ACOS(" +
            "    COS(RADIANS(#{lat})) * COS(RADIANS(latitude)) *" +
            "    COS(RADIANS(longitude) - RADIANS(#{lng})) +" +
            "    SIN(RADIANS(#{lat})) * SIN(RADIANS(latitude))" +
            "  )) AS distance_km " +
            "FROM national_hub " +
            "WHERE status = 0 AND hub_level = 2 " +
            "ORDER BY distance_km ASC " +
            "LIMIT 1")
    NationalHub selectNearestCityHub(@Param("lat") double lat, @Param("lng") double lng);

    /** 查询所有启用的Hub（供MCMF构建图） */
    @Select("SELECT * FROM national_hub WHERE status != 2")
    List<NationalHub> selectAllActive();
}
