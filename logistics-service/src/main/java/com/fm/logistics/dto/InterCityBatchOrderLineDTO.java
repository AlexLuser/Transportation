package com.fm.logistics.dto;

import lombok.Data;

/**
 * 干线批次内已绑定的实单行（与 order_info 一致，用于前端「发车/到达影响范围」展示）
 */
@Data
public class InterCityBatchOrderLineDTO {
    private Long orderId;
    private String orderNo;
    /** 0~5 与系统订单状态一致 */
    private Integer orderStatus;
    private String originHubName;
    private String destHubName;
}
