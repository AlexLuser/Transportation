package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("logistics_track")
public class LogisticsTrack {
    /*主键*/
    @TableId(type = IdType.AUTO)
    private Long id;
    /*路线ID*/
    private Long routeId;
    /*运输员ID*/
    private Long driverId;
    /*纬度*/
    private Double latitude;
    /*经度*/
    private Double longitude;
    /*海拔*/
    private Double altitude;
    /*速度*/
    private Double speed;
    /*方向角*/
    private Double heading;
    /*GPS精度*/
    private Double accuracy;
    /*位置描述*/
    private String address;
    /*GPS上报时间*/
    private Date trackTime;
    /*创建时间*/
    private Date createTime;
}
