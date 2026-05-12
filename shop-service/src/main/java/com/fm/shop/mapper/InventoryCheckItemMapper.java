package com.fm.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.shop.entity.InventoryCheckItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface InventoryCheckItemMapper extends BaseMapper<InventoryCheckItem> {

    /** 统计盘点差异汇总 */
    @Select("SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN actual_qty IS NOT NULL THEN 1 ELSE 0 END) AS counted, " +
            "SUM(CASE WHEN actual_qty IS NOT NULL AND actual_qty != system_qty THEN 1 ELSE 0 END) AS diffCount " +
            "FROM inventory_check_item WHERE check_id = #{checkId}")
    Map<String, Object> selectCheckSummary(@Param("checkId") Long checkId);
}
