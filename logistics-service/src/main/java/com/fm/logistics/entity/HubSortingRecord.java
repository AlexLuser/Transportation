package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * Hub分拣记录
 * 记录货物在配送中心的分拣作业（决定走哪趟干线批次）
 */
@Data
@TableName("hub_sorting_record")
public class HubSortingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hubId;

    private Long orderId;

    private String waybillNo;

    /** 目的配送中心ID */
    private Long destHubId;

    /** 分配到的干线批次ID */
    private Long assignedBatchId;

    /** PENDING=待分拣，ASSIGNED=已分配批次，DIRECT=直送末端 */
    private String sortResult;

    private Long operatorId;

    private Date sortTime;
    private Date createTime;
    private Date updateTime;
}
