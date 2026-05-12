package com.fm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商户信息（user-service 内部用于审核状态查询与更新）
 */
@Data
@TableName("shop_info")
public class ShopReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String shopName;

    private String shopPhone;

    private String shopEmail;

    private String businessLicense;

    /** 状态：0=禁用，1=启用，2=待审核 */
    private Integer status;
}
