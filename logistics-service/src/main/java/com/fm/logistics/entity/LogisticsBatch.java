package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 配送批次实体
 * 对应数据库表：logistics_batch
 *
 * 一个批次 = 多个订单合并 + 一条干线（仓库→Hub）+ N 条末端路线（Hub→各客户）
 *
 * 批次状态流转：
 *   0(待出发) → 1(干线运输中) → 2(已到中转站) → 3(末端派送中) → 4(全部完成)
 */
@Data
@TableName("logistics_batch")
public class LogisticsBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次编号（LB+yyyyMMddHHmmss+4位随机） */
    private String batchNo;

    /** 发货仓库ID */
    private Long warehouseId;

    /** 中转站ID（useHub=true 时有值） */
    private Long hubId;

    /** 干线路线ID（segment_type=1 的 logistics_route.id） */
    private Long trunkRouteId;

    /**
     * 批次状态：
     *   0=待出发，1=干线运输中，2=已到中转站，3=末端派送中，4=全部完成
     */
    private Integer batchStatus;

    /** 批次内订单总数 */
    private Integer totalOrders;

    /**
     * 是否经 Hub 中转：0=否（干线司机直接多点配送），1=是
     */
    private Integer useHub;

    /** 使用的 VRP 算法名称 */
    private String vrpAlgorithm;

    /** VRP 规划总距离（米） */
    private Double totalDistance;

    /** 备注 */
    private String remark;

    private Date createTime;
    private Date updateTime;
}
