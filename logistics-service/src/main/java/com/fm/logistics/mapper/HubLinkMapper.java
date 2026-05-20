package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.HubLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HubLinkMapper extends BaseMapper<HubLink> {

    /** 查询所有启用的运输边（供MCMF构建图） */
    @Select("SELECT * FROM hub_link WHERE is_active = 1")
    List<HubLink> selectActiveLinks();
}
