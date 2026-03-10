package com.fm.common.constant;

/**
 * 权限枚举
 * 对应数据库user表中的permission字段
 */
public enum PermissionEnum {
    ADMIN(1, "管理员", "admin", "/admin/dashboard"),
    CUSTOMER(2, "顾客用户", "customer", "/customer/home"),
    SHOP(3, "商户用户", "shop", "/shop/home"),
    DRIVER(4, "运输员", "driver", "/driver/orders");

    private final Integer permission;
    private final String roleName;
    private final String roleCode;
    private final String redirectPath;

    PermissionEnum(Integer permission, String roleName, String roleCode, String redirectPath) {
        this.permission = permission;
        this.roleName = roleName;
        this.roleCode = roleCode;
        this.redirectPath = redirectPath;
    }

    public Integer getPermission() {
        return permission;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRedirectPath() {
        return redirectPath;
    }

    /**
     * 根据权限值获取枚举
     */
    public static PermissionEnum getByPermission(Integer permission) {
        for (PermissionEnum permissionEnum : values()) {
            if (permissionEnum.getPermission().equals(permission)) {
                return permissionEnum;
            }
        }
        return null;
    }
}

