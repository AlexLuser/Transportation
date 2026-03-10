package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 物流节点实体
 * 记录路线中的关键里程碑节点：出发点、途经点、目的地
 * 用于分段展示物流进度（类似快递物流详情页）
 */
@Data
@TableName("logistics_node")
public class LogisticsNode {

    /** 节点ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 logistics_route 表的 id */
    private Long routeId;

    /**
     * 节点类型：
     * 0 = 出发点（仓库）
     * 1 = 途经点（中转、休息站等）
     * 2 = 目的地（收货地址）
     */
    private Integer nodeType;

    /** 节点名称（如"北京中央仓库"、"济南中转站"、"收货地址"） */
    private String nodeName;

    /** 节点地址 */
    private String nodeAddress;

    /** 节点纬度 */
    private Double latitude;

    /** 节点经度 */
    private Double longitude;

    /** 顺序号（从小到大排列节点顺序） */
    private Integer sequenceNo;

    /** 计划到达时间 */
    private Date plannedArriveTime;

    /** 实际到达时间 */
    private Date actualArriveTime;

    /**
     * 节点状态：
     * 0 = 未到达
     * 1 = 已到达
     * 2 = 已跳过（路线变更时使用）
     */
    private Integer nodeStatus;

    /** 节点备注（如"货物已在此中转"） */
    private String remark;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}

