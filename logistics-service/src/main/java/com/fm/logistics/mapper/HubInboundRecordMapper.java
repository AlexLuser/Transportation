package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.HubInboundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HubInboundRecordMapper extends BaseMapper<HubInboundRecord> {

    @Select("SELECT * FROM hub_inbound_record WHERE hub_id = #{hubId} ORDER BY create_time DESC LIMIT #{limit}")
    List<HubInboundRecord> selectRecentByHub(@Param("hubId") Long hubId, @Param("limit") int limit);
}
