package com.fm.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    USERNAME_OR_PASSWORD_ERROR(4001, "用户名或密码错误"),
    USER_DISABLED(4002, "用户已被禁用"),
    TOKEN_INVALID(4003, "Token无效或已过期");

    private final Integer code;
    private final String message;
}