package com.fm.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.shop.entity.Warehouse;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仓库信息Mapper接口
 * 提供仓库信息的数据库操作
 */
@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {
}

