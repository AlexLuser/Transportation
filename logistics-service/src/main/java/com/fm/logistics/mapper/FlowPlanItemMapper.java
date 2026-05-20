package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.FlowPlanItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FlowPlanItemMapper extends BaseMapper<FlowPlanItem> {

    @Select("SELECT * FROM flow_plan_item WHERE plan_id = #{planId} AND flow_amount > 0")
    List<FlowPlanItem> selectByPlanId(@Param("planId") Long planId);
}
