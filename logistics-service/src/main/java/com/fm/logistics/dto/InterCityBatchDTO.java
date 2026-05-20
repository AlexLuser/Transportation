package com.fm.logistics.dto;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 跨城干线批次详情（前端展示用）
 */
@Data
public class InterCityBatchDTO {

    private Long id;
    private String batchNo;
    private Long flowPlanId;

    private Long fromHubId;
    private String fromHubName;
    private String fromCity;

    private Long toHubId;
    private String toHubName;
    private String toCity;

    private String transportMode;
    private Date plannedDepart;
    private Date actualDepart;
    private Date actualArrive;
    private String status;
    private Integer itemCount;
    private String remark;

    /** 本批次绑定的实单（发车/到达会批量更新此列表内订单，与 MCMF 边流无必然对应） */
    private List<InterCityBatchOrderLineDTO> orders;
}
