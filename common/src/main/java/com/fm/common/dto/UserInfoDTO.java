package com.fm.common.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserInfoDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String roleCode;
    private String roleName;
}
