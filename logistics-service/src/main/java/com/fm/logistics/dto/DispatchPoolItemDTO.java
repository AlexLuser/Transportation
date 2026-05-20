package com.fm.logistics.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度池中单个待调度订单的展示 DTO
 */
@Data
public class DispatchPoolItemDTO {
    private Long   poolId;
    private Long   orderId;
    private Long   shopId;
    private Long   warehouseId;
    private String endAddress;
    private Double endLat;
    private Double endLng;
    private String receiverName;
    private String receiverPhone;
    /** 订单备注（用于急送检测提示） */
    private String remark;
    private LocalDateTime enterTime;
    /** 0=待调度 1=已调度 2=已取消 */
    private Integer status;

    // ── MCMF 全国调度扩展字段（干线到达后的跨城单需要这些字段供前端过滤） ──

    /** 发货所在城市 Hub ID（揽收队列按此字段筛选） */
    private Long originHubId;

    /** 收货所在城市 Hub ID */
    private Long destHubId;

    /** 是否跨城：0=同城, 1=跨城 */
    private Integer isCrossCity;

    /**
     * 调度起点类型：
     * 0=仓库（使用 warehouseId 坐标）
     * 1=干线到达 Hub（跨城批次到达后由末端调度）
     */
    private Integer dispatchOriginType;

    /** 实际调度起点纬度（dispatchOriginType=1 时有值） */
    private Double dispatchOriginLat;

    /** 实际调度起点经度 */
    private Double dispatchOriginLng;

    /** 实际调度起点地址 */
    private String dispatchOriginAddr;
}
