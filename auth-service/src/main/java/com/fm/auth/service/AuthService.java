package com.fm.auth.service;

import com.fm.common.dto.LoginRequestDTO;
import com.fm.common.dto.LoginResponseDTO;

public interface AuthService {
    LoginResponseDTO login(LoginRequestDTO loginRequest);
}