package com.fm.logistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 物流轨迹点实体
 * 记录运输员在运输过程中的每个 GPS 位置上报点，形成完整运输轨迹
 */
@Data
@TableName("logistics_track")
public class LogisticsTrack {

    /** 轨迹点ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 logistics_route 表的 id */
    private Long routeId;

    /** 运输员ID（关联 driver_info 表的 id） */
    private Long driverId;

    /** 纬度 */
    private Double latitude;

    /** 经度 */
    private Double longitude;

    /** 海拔（米，可选） */
    private Double altitude;

    /** 速度（km/h，可选） */
    private Double speed;

    /** 方向角（0-360度，0=正北，可选） */
    private Double heading;

    /** GPS精度（米，可选） */
    private Double accuracy;

    /** 当前位置描述（前端逆地理编码后传入，或由服务端处理） */
    private String address;

    /** 位置上报时间（客户端时间，比 createTime 更准确） */
    private Date trackTime;

    /** 记录创建时间 */
    private Date createTime;
}

