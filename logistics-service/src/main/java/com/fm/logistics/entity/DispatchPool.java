package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度池实体 — 商户备货完成后，订单在此等待智能调度分批
 * status: 0=待调度 1=已调度 2=已取消
 */
@Data
@TableName("dispatch_pool")
public class DispatchPool {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long shopId;
    private Long warehouseId;

    private String endAddress;
    private Double endLat;
    private Double endLng;

    private String receiverName;
    private String receiverPhone;

    /** 订单备注（来自原始订单，用于急送检测） */
    private String remark;

    /** 0=待调度 1=已调度 2=已取消 */
    private Integer status;

    /** 调度后关联的批次ID */
    private Long batchId;

    // ── MCMF 全国调度扩展字段 ────────────────────────────────────────

    /** 发货所在城市Hub ID */
    private Long originHubId;

    /** 收货所在城市Hub ID */
    private Long destHubId;

    /** 是否跨城：0=同城, 1=跨城 */
    private Integer isCrossCity;

    /**
     * 调度起点类型：
     * 0=仓库（使用 warehouseId 坐标）
     * 1=干线到达Hub（使用下方 dispatchOrigin* 坐标）
     */
    private Integer dispatchOriginType;

    /** 实际调度起点纬度（dispatchOriginType=1 时有值） */
    private Double dispatchOriginLat;

    /** 实际调度起点经度 */
    private Double dispatchOriginLng;

    /** 实际调度起点地址 */
    private String dispatchOriginAddr;

    private LocalDateTime enterTime;
    private LocalDateTime dispatchTime;
}
