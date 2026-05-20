package com.fm.common.dto;

import lombok.Data;

/**
 * 用户注册请求DTO
 */
@Data
public class RegisterRequestDTO {

    /** 用户名 */
    private String username;

    /** 密码（明文，后端加密） */
    private String password;

    /** 注册角色：customer / shop / driver */
    private String role;

    // ======== 商户注册专属字段 ========

    /** 商户名称 */
    private String shopName;

    /** 商户联系电话 */
    private String shopPhone;

    /** 商户邮箱 */
    private String shopEmail;

    /** 营业执照号 */
    private String businessLicense;

    // ======== 司机注册专属字段 ========

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 驾驶证号 */
    private String licenseNumber;

    /** 驾驶证类型（C1/C2/B1/B2等） */
    private String licenseType;

    /** 驾驶证到期日期（yyyy-MM-dd） */
    private String licenseExpireDate;
}
