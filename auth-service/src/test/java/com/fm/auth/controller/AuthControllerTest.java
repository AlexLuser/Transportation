package com.fm.auth.controller;

import com.fm.auth.service.AuthService;
import com.fm.common.dto.LoginRequestDTO;
import com.fm.common.dto.LoginResponseDTO;
import com.fm.common.dto.UserInfoDTO;
import com.fm.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 集成测试
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试登录接口成功
     */
    @Test
    void testLoginSuccess() throws Exception {
        // 准备测试数据
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("123456");

        // Mock AuthService 返回结果
        LoginResponseDTO loginResponse = new LoginResponseDTO();
        loginResponse.setToken("test.jwt.token");
        loginResponse.setExpiration(7200L);
        
        UserInfoDTO userInfo = new UserInfoDTO();
        userInfo.setUserId(1L);
        userInfo.setUsername("admin");
        userInfo.setRoleCode("admin");
        userInfo.setRoleName("管理员");
        
        loginResponse.setUserInfo(userInfo);

        when(authService.login(any(LoginRequestDTO.class))).thenReturn(loginResponse);

        // 执行请求并验证
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.token").value("test.jwt.token"))
                .andExpect(jsonPath("$.data.userInfo.userId").value(1))
                .andExpect(jsonPath("$.data.userInfo.username").value("admin"))
                .andExpect(jsonPath("$.data.userInfo.roleCode").value("admin"));
    }

    /**
     * 测试登录接口 - 参数为空
     */
    @Test
    void testLoginWithEmptyRequest() throws Exception {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("");
        loginRequest.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk()); // 由于有全局异常处理器，会返回200但code为500
    }
}

