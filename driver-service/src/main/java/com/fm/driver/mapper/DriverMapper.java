package com.fm.driver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.driver.entity.Driver;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运输员信息Mapper接口
 */
@Mapper
public interface DriverMapper extends BaseMapper<Driver> {
}

