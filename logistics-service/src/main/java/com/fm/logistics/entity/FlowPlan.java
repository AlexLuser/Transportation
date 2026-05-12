package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * MCMF 流量规划单
 * status: PENDING / OPTIMIZING / DONE / FAILED
 */
@Data
@TableName("flow_plan")
public class FlowPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate planDate;

    private Integer totalDemand;

    private BigDecimal totalCost;

    /** MCMF 实际完成总流量（件），与 totalDemand 相等表示图上传输需求全部满足 */
    private Integer actualFlow;

    /** 1=可行（流量打满需求） 0=不可行（部分枢纽在图上无入边/路径不足等） */
    private Integer feasible;

    /** PENDING / OPTIMIZING / DONE / FAILED */
    private String status;

    private String algorithm;

    /** LLM 风险分析与调度建议（可为空） */
    private String llmAdvice;

    /** 0=未使用LLM校准, 1=已使用 */
    private Integer llmEnhanced;

    private Date createTime;
    private Date updateTime;
}
