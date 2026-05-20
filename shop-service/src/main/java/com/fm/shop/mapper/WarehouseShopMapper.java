package com.fm.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.shop.entity.WarehouseShop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WarehouseShopMapper extends BaseMapper<WarehouseShop> {

    /** 查询商家有权限的仓库列表（含仓库基本信息） */
    @Select("SELECT w.id, w.warehouse_name AS warehouseName, w.province, w.city, w.district, " +
            "w.detail_address AS detailAddress, w.capacity, w.status, w.latitude, w.longitude, " +
            "w.affiliated_hub_id AS affiliatedHubId, ws.role " +
            "FROM warehouse_shop ws " +
            "JOIN warehouse w ON ws.warehouse_id = w.id " +
            "WHERE ws.shop_id = #{shopId} AND ws.status = 1 AND w.status = 1 " +
            "ORDER BY ws.role DESC, w.id ASC")
    List<Map<String, Object>> selectWarehousesByShopId(@Param("shopId") Long shopId);

    /** 查询仓库关联的商家列表（含商家基本信息） */
    @Select("SELECT s.id, s.shop_name AS shopName, s.shop_phone AS shopPhone, ws.role, ws.status " +
            "FROM warehouse_shop ws " +
            "JOIN shop_info s ON ws.shop_id = s.id " +
            "WHERE ws.warehouse_id = #{warehouseId} " +
            "ORDER BY ws.role DESC, s.id ASC")
    List<Map<String, Object>> selectShopsByWarehouseId(@Param("warehouseId") Long warehouseId);
}
