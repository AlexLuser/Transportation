package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * Hub到货入库记录
 * 记录货物到达配送中心（national_hub）后的入库操作
 * source_type: COLLECTION=揽收到仓，TRUNK_ARRIVE=干线到达
 */
@Data
@TableName("hub_inbound_record")
public class HubInboundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配送中心ID（关联national_hub.id） */
    private Long hubId;

    private Long orderId;

    private String waybillNo;

    /** COLLECTION=揽收，TRUNK_ARRIVE=干线到达 */
    private String sourceType;

    /** 来源Hub ID（干线到达时有效） */
    private Long fromHubId;

    private Long operatorId;

    /** PENDING=待入库，DONE=已入库 */
    private String status;

    private Date arriveTime;
    private Date inboundTime;
    private Date createTime;
    private Date updateTime;
}
