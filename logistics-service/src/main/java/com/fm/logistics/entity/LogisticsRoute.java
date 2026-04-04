package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("logistics_route")
public class LogisticsRoute {
    /*主键*/
    @TableId(type = IdType.AUTO)
    private Long id;
    /*物流单号*/
    private String routeNo;
    /*订单ID*/
    private Long orderId;
    /*配送ID*/
    private Long deliveryId;
    /*运输员ID*/
    private Long driverId;
    /*仓库ID*/
    private Long warehouseId;
    /*出发地信息*/
    private String startAddress;
    /*出发地纬度（DB列名 start_lat）*/
    @TableField("start_lat")
    private Double startLatitude;
    /*出发地经度（DB列名 start_lng）*/
    @TableField("start_lng")
    private Double startLongitude;
    /*目的地信息*/
    private String endAddress;
    /*目的地纬度（DB列名 end_lat）*/
    @TableField("end_lat")
    private Double endLatitude;
    /*目的地经度（DB列名 end_lng）*/
    @TableField("end_lng")
    private Double endLongitude;
    /*当前位置纬度（DB列名 current_lat）*/
    @TableField("current_lat")
    private Double currentLatitude;
    /*当前位置经度（DB列名 current_lng）*/
    @TableField("current_lng")
    private Double currentLongitude;
    /*当前位置描述*/
    private String currentAddress;
    /*最后一次位置更新时间*/
    private Date lastTrackTime;
    /*路线状态*/
    private Integer routeStatus;

    /*预计到达时间*/
    private Date estimatedArrivalTime;
    /*实际到达时间*/
    private Date actualArrivalTime;
    /*计划路线*/
    private String plannedRoute;
    /*收货人姓名*/
    private String receiverName;
    /*收货人电话*/
    private String receiverPhone;
    /*备注*/
    private String remark;
    /*创建时间*/
    private Date createTime;
    /*更新时间*/
    private Date updateTime;
}
