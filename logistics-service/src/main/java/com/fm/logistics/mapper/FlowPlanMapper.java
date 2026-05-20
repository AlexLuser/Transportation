package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.FlowPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface FlowPlanMapper extends BaseMapper<FlowPlan> {

    /** 查询最新一条 DONE 状态的规划单 */
    @Select("SELECT * FROM flow_plan WHERE status = 'DONE' ORDER BY plan_date DESC, id DESC LIMIT 1")
    FlowPlan selectLatestDone();

    /** 指定业务日下最新一条 DONE 规划（用于取「服务器昨日」边流量基线） */
    @Select("SELECT * FROM flow_plan WHERE status = 'DONE' AND plan_date = #{planDate} ORDER BY id DESC LIMIT 1")
    FlowPlan selectLatestDoneOnDate(@Param("planDate") LocalDate planDate);
}
