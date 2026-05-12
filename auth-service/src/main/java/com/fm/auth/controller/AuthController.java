package com.fm.auth.controller;

import com.fm.auth.service.AuthService;
import com.fm.common.dto.LoginRequestDTO;
import com.fm.common.dto.LoginResponseDTO;
import com.fm.common.dto.RegisterRequestDTO;
import com.fm.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * RESTful风格接口
 */
@Tag(name = "认证管理", description = "登录、登出等认证相关接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 用户登录
     * RESTful: POST /api/auth/login
     */
    @Operation(summary = "用户登录", description = "通过用户名和密码登录，返回JWT Token")
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@RequestBody LoginRequestDTO loginRequest) {
        LoginResponseDTO loginResponse = authService.login(loginRequest);
        return Result.success(loginResponse);
    }

    /**
     * 用户登出
     * RESTful: POST /api/auth/logout
     */
    @Operation(summary = "用户登出", description = "用户登出，前端清除本地Token")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    /**
     * 用户注册（顾客直接通过；商户/司机需管理员审核）
     * RESTful: POST /api/auth/register
     */
    @Operation(summary = "用户注册", description = "支持顾客、商户、司机三种角色注册")
    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterRequestDTO registerRequest) {
        authService.register(registerRequest);
        return Result.success();
    }
}

