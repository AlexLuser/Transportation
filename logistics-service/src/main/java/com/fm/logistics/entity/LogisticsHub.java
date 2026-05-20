package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 物流中转站实体
 * 对应数据库表：logistics_hub
 *
 * 节点类型说明（logistics_node.node_type）：
 *   0=出发点  1=途经点  2=目的地  3=中转站（Hub）
 */
@Data
@TableName("logistics_hub")
public class LogisticsHub {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 中转站名称 */
    private String name;

    /** 详细地址 */
    private String address;

    /** 纬度 */
    private Double latitude;

    /** 经度 */
    private Double longitude;

    /** 所属区域（如：浦东、虹桥、松江） */
    private String region;

    /** 最大日处理包裹量 */
    private Integer maxCapacity;

    /** 当前待处理包裹数 */
    private Integer currentLoad;

    /**
     * 状态：0=正常，1=满载，2=关闭
     */
    private Integer status;

    /** 备注 */
    private String remark;

    private Date createTime;
    private Date updateTime;
}
