package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 跨城干线批次
 * status: CREATED → DEPARTED → ARRIVED → DISPATCHED
 */
@Data
@TableName("inter_city_batch")
public class InterCityBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** IB+时间戳+4位随机 */
    private String batchNo;

    private Long flowPlanId;
    private Long fromHubId;
    private Long toHubId;

    /** ROAD / RAIL / AIR */
    private String transportMode;

    private Date plannedDepart;
    private Date actualDepart;
    private Date actualArrive;

    /** CREATED / DEPARTED / ARRIVED / DISPATCHED */
    private String status;

    private Integer itemCount;
    private String remark;

    private Date createTime;
    private Date updateTime;
}
