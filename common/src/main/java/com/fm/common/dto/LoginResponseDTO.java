package com.fm.common.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class LoginResponseDTO {
    private static final long serialVersionUID = 1L;

    private String token;
    private UserInfoDTO userInfo;
    private Long expiration;
}
