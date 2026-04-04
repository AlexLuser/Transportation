package com.fm.logistics.dto;

import com.fm.logistics.entity.LogisticsNode;
import com.fm.logistics.entity.LogisticsRoute;
import com.fm.logistics.entity.LogisticsTrack;
import lombok.Data;
import java.util.List;

/**
 * 物流路线详情 DTO
 * 买家、运输员、管理员查询路线时返回此对象
 * 聚合了路线主信息 + 节点列表 + 近期轨迹
 */
@Data
public class RouteDetailDTO {

    /** 路线主信息 */
    private LogisticsRoute route;

    /** 里程碑节点列表（按 sequenceNo 升序：出发点 → 途经点 → 目的地） */
    private List<LogisticsNode> nodes;

    /** 最近 50 条轨迹（时间倒序，用于地图展示近期路径段） */
    private List<LogisticsTrack> recentTracks;

    /** 路线状态中文描述（如："运输中"、"已送达"） */
    private String statusDesc;

    /** 配送员姓名（从 driver-service 获取，未绑定时为 null） */
    private String driverName;

    /** 配送员联系电话（从 driver-service 获取，未绑定时为 null） */
    private String driverPhone;
}

