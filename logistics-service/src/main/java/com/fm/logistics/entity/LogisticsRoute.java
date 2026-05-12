package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 物流路线表实体
 *
 * 兼容三种路线类型（segment_type）：
 *   0 = 独立单订单路线（orderId 必填，stop_count=1，waypoints=null）
 *   1 = 干线路线（仓库 → Hub，orderId=null，stop_count=1）
 *   2 = 末端路线（Hub/仓库 → 客户，单订单时 orderId 必填；多停靠时 orderId=null，
 *                stop_count>1，waypoints 存有序停靠点列表）
 */
@Data
@TableName("logistics_route")
public class LogisticsRoute {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 路线编号（LR+yyyyMMddHHmmss+4位随机） */
    private String routeNo;

    /**
     * 关联订单ID
     *   单订单路线（type=0/2,stop=1）：具体订单ID
     *   多停靠末端路线（type=2,stop>1）：null，详见 waypoints
     *   干线路线（type=1）：null
     */
    private Long orderId;

    /** 关联配送记录ID（接单后绑定） */
    private Long deliveryId;

    /** 运输员ID（接单后绑定） */
    private Long driverId;

    /** 出发仓库ID */
    private Long warehouseId;

    // ── Hub-and-Spoke 字段 ───────────────────────────────────────

    /** 所属批次ID（null 表示单订单独立模式） */
    private Long batchId;

    /**
     * 路线段类型：
     *   0 = 独立单订单
     *   1 = 干线（仓库→Hub，GraphHopper规划）
     *   2 = 末端（Hub→客户，GraphHopper规划）
     *   3 = 跨城干线（Hub→Hub，直线虚拟路线，不经GraphHopper）
     */
    private Integer segmentType;

    /** 中转站ID（干线=目标Hub，末端=起点Hub） */
    private Long hubId;

    /** 关联跨城干线批次ID（segmentType=3 时有值） */
    private Long interCityBatchId;

    // ── 多停靠末端路线分组字段（segment_type=2 时有效）───────────

    /** 批次内末端分组编号（0,1,2...），同一组由一名司机完成 */
    private Integer groupIndex;

    /** 本路线停靠点数（1=单订单，>1=多停靠末端路线） */
    private Integer stopCount;

    /**
     * 多停靠点有序列表（JSON字符串，stop_count>1 时有效）
     * 格式：[{seq,orderId,address,lat,lng,receiverName,receiverPhone}, ...]
     */
    private String waypoints;

    // ── 出发地信息 ───────────────────────────────────────────────

    @TableField("start_address")
    private String startAddress;

    @TableField("start_lat")
    private Double startLatitude;

    @TableField("start_lng")
    private Double startLongitude;

    // ── 目的地信息（多停靠末端路线 = 最后一个停靠点）────────────

    @TableField("end_address")
    private String endAddress;

    @TableField("end_lat")
    private Double endLatitude;

    @TableField("end_lng")
    private Double endLongitude;

    // ── 实时位置 ─────────────────────────────────────────────────

    @TableField("current_lat")
    private Double currentLatitude;

    @TableField("current_lng")
    private Double currentLongitude;

    private String currentAddress;
    private Date lastTrackTime;

    // ── 状态 & 时间 ──────────────────────────────────────────────

    /** -1=待激活，0=待出发，1=运输中，2=已送达，3=异常 */
    private Integer routeStatus;

    private Date estimatedArrivalTime;
    private Date actualArrivalTime;

    /** 计划路线（GeoJSON LineString，由 GraphHopper 生成） */
    private String plannedRoute;

    /**
     * 收货人姓名（单订单路线快照；多停靠末端路线为null，收货人信息见 waypoints/batch_item）
     */
    private String receiverName;

    /**
     * 收货人电话（单订单路线快照；多停靠末端路线为null）
     */
    private String receiverPhone;

    private String remark;
    private Date createTime;
    private Date updateTime;
}
