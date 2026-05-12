package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * Hub出库记录
 * 记录货物从配送中心出库（发往干线批次或末端配送）
 */
@Data
@TableName("hub_outbound_record")
public class HubOutboundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hubId;

    private Long orderId;

    private String waybillNo;

    /** TRUNK=干线发车，LAST_MILE=末端下发 */
    private String destType;

    /** 目的Hub ID（干线时有效） */
    private Long destHubId;

    /** 批次ID（inter_city_batch 或 logistics_batch） */
    private Long batchId;

    private Long operatorId;

    /** PENDING=待出库，DONE=已出库 */
    private String status;

    private Date outboundTime;
    private Date createTime;
    private Date updateTime;
}
