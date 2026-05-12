package com.fm.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.shop.entity.WarehouseLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WarehouseLocationMapper extends BaseMapper<WarehouseLocation> {

    /** 按区域统计库位占用情况 */
    @Select("SELECT zone_code AS zoneCode, " +
            "COUNT(*) AS totalLocations, " +
            "SUM(capacity) AS totalCapacity, " +
            "SUM(current_stock) AS totalStock, " +
            "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS activeLocations " +
            "FROM warehouse_location " +
            "WHERE warehouse_id = #{warehouseId} " +
            "GROUP BY zone_code ORDER BY zone_code")
    List<java.util.Map<String, Object>> selectZoneSummary(@Param("warehouseId") Long warehouseId);
}
