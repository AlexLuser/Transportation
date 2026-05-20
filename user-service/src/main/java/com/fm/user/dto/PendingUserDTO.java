package com.fm.user.dto;

import lombok.Data;

/**
 * 待审核用户 DTO（供管理员审核页面展示）
 */
@Data
public class PendingUserDTO {

    /** user.id */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色：shop / driver */
    private String role;

    // ======== 商户字段 ========
    private String shopName;
    private String shopPhone;
    private String shopEmail;
    private String businessLicense;

    // ======== 司机字段 ========
    private String realName;
    private String phone;
    private String licenseNumber;
    private String licenseType;
    private String licenseExpireDate;

    /** 详情记录 ID（shop_info.id 或 driver_info.id） */
    private Long detailId;
}
