package com.fm.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.shop.entity.WarehouseLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 在约束下调整库位占用：归属仓库一致、状态=正常、占用 ∈ [0, capacity]（capacity&gt;0 时上限生效）。
     * @return 影响行数，0 表示条件不满足（越界、状态不符等）
     */
    @Update("UPDATE warehouse_location SET current_stock = current_stock + #{delta} " +
            "WHERE id = #{locationId} AND warehouse_id = #{warehouseId} AND status = 1 " +
            "AND (current_stock + #{delta}) >= 0 " +
            "AND (#{delta} <= 0 OR capacity IS NULL OR capacity <= 0 OR (current_stock + #{delta}) <= capacity)")
    int adjustCurrentStockBounded(@Param("locationId") Long locationId,
                                  @Param("warehouseId") Long warehouseId,
                                  @Param("delta") int delta);
}
