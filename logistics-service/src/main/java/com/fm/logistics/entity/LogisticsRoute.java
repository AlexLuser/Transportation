package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 物流路线实体
 * 一个订单对应一条物流路线，记录从仓库到收货地址的全程信息
 */
@Data
@TableName("logistics_route")
public class LogisticsRoute {

    /** 路线ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 路线编号（唯一，格式：LR + 时间戳 + 4位随机数） */
    private String routeNo;

    /** 关联 order_info 表的 id */
    private Long orderId;

    /** 关联 order_delivery 表的 id（接单后绑定） */
    private Long deliveryId;

    /** 关联 driver_info 表的 id（接单后绑定） */
    private Long driverId;

    /** 出发仓库ID */
    private Long warehouseId;

    /** 出发地址（仓库地址，快照） */
    private String startAddress;

    /** 出发地纬度（可选） */
    private Double startLat;

    /** 出发地经度（可选） */
    private Double startLng;

    /** 目的地地址（收货地址，快照） */
    private String endAddress;

    /** 目的地纬度（可选） */
    private Double endLat;

    /** 目的地经度（可选） */
    private Double endLng;

    /** 当前位置纬度（实时更新） */
    private Double currentLat;

    /** 当前位置经度（实时更新） */
    private Double currentLng;

    /** 当前位置描述（逆地理编码后的地址，实时更新） */
    private String currentAddress;

    /** 最后位置更新时间 */
    private Date lastTrackTime;

    /**
     * 路线状态：
     * 0 = 待出发（配送记录已创建，尚未有运输员接单）
     * 1 = 运输中（运输员已接单并开始配送）
     * 2 = 已送达（货物已到达目的地）
     * 3 = 异常（路线偏离、超时等）
     */
    private Integer routeStatus;

    /** 计划距离（km，创建时估算） */
    private Double plannedDistance;

    /** 实际距离（km，运输完成后统计） */
    private Double actualDistance;

    /** 计划运输时长（分钟，创建时估算） */
    private Integer plannedDuration;

    /** 预计到达时间（动态更新） */
    private Date estimatedArrivalTime;

    /** 实际到达时间 */
    private Date actualArrivalTime;

    /**
     * 计划路线（GeoJSON LineString 格式的 JSON 字符串）
     * 示例：{"type":"LineString","coordinates":[[lng,lat],[lng,lat],...]}
     * 初始为空，由人工指定或由 AI 接口填入
     */
    private String plannedRoute;

    /**
     * AI建议路线（JSON 字符串，预留给大模型路线规划使用）
     * 格式与 plannedRoute 相同，但包含额外的 AI 分析信息
     */
    private String aiSuggestedRoute;

    /**
     * AI分析结果（JSON 字符串，预留给大模型输出）
     * 包含风险评估、ETA置信度、异常警告等
     */
    private String aiAnalysis;

    /** 收货人姓名（快照） */
    private String receiverName;

    /** 收货人电话（快照） */
    private String receiverPhone;

    /** 路线备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}

