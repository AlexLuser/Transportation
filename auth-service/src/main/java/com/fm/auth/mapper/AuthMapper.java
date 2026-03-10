package com.fm.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.common.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthMapper extends BaseMapper<User> {
}
