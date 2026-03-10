package com.fm.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.customer.entity.Address;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址Mapper接口
 */
@Mapper
public interface AddressMapper extends BaseMapper<Address> {
}

