package com.fm.auth.service;

import com.fm.common.dto.LoginRequestDTO;
import com.fm.common.dto.LoginResponseDTO;
import com.fm.common.dto.RegisterRequestDTO;

public interface AuthService {
    LoginResponseDTO login(LoginRequestDTO loginRequest);

    void logout();

    void register(RegisterRequestDTO registerRequest);
}