package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MCMF 流量规划明细（每条边的分配流量）
 */
@Data
@TableName("flow_plan_item")
public class FlowPlanItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;
    private Long fromHubId;
    private Long toHubId;
    private Long linkId;

    /** 分配流量（件） */
    private Integer flowAmount;

    /** 该边单位费用 */
    private BigDecimal edgeCost;

    /** 该边总费用 = flowAmount * edgeCost */
    private BigDecimal totalCost;

    private Date createTime;
}
