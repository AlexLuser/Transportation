package com.fm.driver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 订单配送实体类
 * 对应数据库表：order_delivery
 */
@Data
@TableName("order_delivery")
public class OrderDelivery {
    /** 配送ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 关联order_info表的id */
    private Long orderId;
    
    /** 关联driver_info表的id（运输员，接单后才有） */
    private Long driverId;
    
    /** 关联vehicle_info表的id（使用的车辆） */
    private Long vehicleId;
    
    /** 配送状态：0=待接单，1=已接单，2=运输中，3=已送达，4=已取消 */
    private Integer deliveryStatus;
    
    /** 接单时间 */
    private Date acceptTime;
    
    /** 取货时间 */
    private Date pickupTime;
    
    /** 送达时间 */
    private Date deliveryTime;
    
    /** 取消时间 */
    private Date cancelTime;
    
    /** 取消原因 */
    private String cancelReason;
    
    /** 配送地址（快照） */
    private String deliveryAddress;
    
    /** 收货人姓名（快照） */
    private String receiverName;
    
    /** 收货人电话（快照） */
    private String receiverPhone;
    
    /** 配送备注 */
    private String remark;

    // ── Hub-and-Spoke 扩展字段 ──────────────────────────────────
    /**
     * 配送段类型：
     *   0 = 完整单订单（默认，兼容原有流程）
     *   1 = 干线（仓库 → Hub，目的地是 Hub）
     *   2 = 末端（Hub → 客户）
     */
    private Integer segmentType;

    /** 所属批次ID */
    private Long batchId;

    /** 中转站ID（干线司机的目标 Hub） */
    private Long hubId;

    /**
     * 关联的物流路线ID（干线路线接单时使用，末端/普通路线通过 orderId 查路线）
     * 干线路线 orderId=null，必须通过 routeId 直接绑定
     */
    private Long routeId;

    /** 创建时间 */
    private Date createTime;
    
    /** 更新时间 */
    private Date updateTime;
}
















