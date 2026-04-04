package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("logistics_node")
public class LogisticsNode {
    /*主键*/
    @TableId(type = IdType.AUTO)
    private Long id;
    /*路线ID*/
    private Long routeId;
    /*节点类型*/
    private Integer nodeType;
    /*节点名称*/
    private String nodeName;
    /*节点地址*/
    private String nodeAddress;
    /*节点纬度*/
    private Double latitude;
    /*节点经度*/
    private Double longitude;
    /*顺序号*/
    private Integer sequenceNo;
    /*计划到达时间*/
    private Date plannedArriveTime;
    /*实际到达时间*/
    private Date actualArriveTime;
    /*节点状态*/
    private Integer nodeStatus;
    /*备注*/
    private String remark;
    /*创建时间*/
    private Date createTime;
    /*更新时间*/
    private Date updateTime;
}
