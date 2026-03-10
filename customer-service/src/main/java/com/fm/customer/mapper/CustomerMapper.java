package com.fm.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.customer.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 顾客信息Mapper接口
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}

