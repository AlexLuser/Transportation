package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 批次订单明细实体
 *
 * 每行代表批次内一个订单（= 末端路线的一个停靠点）。
 *
 * 状态流转：0(待激活) → 1(末端派送中) → 2(已送达)
 *
 * 与 logistics_route 的关系：
 *   单停靠：route_id 指向一条 stop_count=1 的 logistics_route
 *   多停靠：同组所有 item 共享同一个 route_id（stop_count>1 的组路线）
 */
@Data
@TableName("logistics_batch_item")
public class LogisticsBatchItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属批次ID */
    private Long batchId;

    /** 订单ID */
    private Long orderId;

    /** 所属末端路线ID（单停靠=单条route；多停靠=同组 item 共享同一 route） */
    private Long routeId;

    /** 批次内全局 VRP 访问顺序（从1开始） */
    private Integer visitSequence;

    /** 在同组末端路线内的停靠顺序（从1开始，多停靠时有效） */
    private Integer stopSequence;

    /** 目的地纬度（快照） */
    private Double endLat;

    /** 目的地经度（快照） */
    private Double endLng;

    /** 目的地地址（快照） */
    private String endAddress;

    /** 收货人姓名（快照） */
    private String receiverName;

    /** 收货人电话（快照） */
    private String receiverPhone;

    /** 状态：0=待激活，1=末端派送中，2=已送达 */
    private Integer itemStatus;

    private Date createTime;
    private Date updateTime;
}
