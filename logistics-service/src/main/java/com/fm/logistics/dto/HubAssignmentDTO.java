package com.fm.logistics.dto;

import lombok.Data;

/**
 * Hub 归属分配结果（ShipmentRoutingService 返回值）
 */
@Data
public class HubAssignmentDTO {

    private Long originHubId;
    private Long destHubId;

    /** true=跨城订单，false=同城订单 */
    private boolean crossCity;
}
