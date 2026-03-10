package com.fm.auth.service;

import com.fm.auth.mapper.AuthMapper;
import com.fm.auth.service.impl.AuthServiceImpl;
import com.fm.common.constant.PermissionEnum;
import com.fm.common.dto.LoginRequestDTO;
import com.fm.common.dto.LoginResponseDTO;
import com.fm.common.dto.UserInfoDTO;
import com.fm.common.entity.User;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.common.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthMapper authMapper;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    private BCryptPasswordEncoder passwordEncoder;
    private User testUser;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        // 创建测试用户
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("admin");
        testUser.setSecret(passwordEncoder.encode("123456")); // 密码：123456
        testUser.setPermission(1); // 管理员权限
    }

    /**
     * 测试成功登录
     */
    @Test
    void testLoginSuccess() {
        // 准备测试数据
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("123456");

        // Mock AuthMapper 返回用户
        when(authMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

        // Mock JwtUtil 返回Token
        String mockToken = "mock.jwt.token";
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn(mockToken);
        when(jwtUtil.getExpiration()).thenReturn(7200L);

        // 执行测试
        LoginResponseDTO result = authService.login(loginRequest);

        // 验证结果
        assertNotNull(result);
        assertEquals(mockToken, result.getToken());
        assertNotNull(result.getUserInfo());
        assertEquals(1L, result.getUserInfo().getUserId());
        assertEquals("admin", result.getUserInfo().getUsername());
        assertEquals("admin", result.getUserInfo().getRoleCode());
        assertEquals("管理员", result.getUserInfo().getRoleName());
        assertEquals(7200L, result.getExpiration());

        // 验证方法调用
        verify(authMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(jwtUtil, times(1)).generateToken(1L, "admin", "admin");
    }

    /**
     * 测试用户名为空
     */
    @Test
    void testLoginWithEmptyUsername() {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("");
        loginRequest.setPassword("123456");

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginRequest);
        });

        assertEquals(ResultCode.FAIL.getCode(), exception.getCode());
        assertEquals("用户名不能为空", exception.getMessage());
    }

    /**
     * 测试密码为空
     */
    @Test
    void testLoginWithEmptyPassword() {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("");

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginRequest);
        });

        assertEquals(ResultCode.FAIL.getCode(), exception.getCode());
        assertEquals("密码不能为空", exception.getMessage());
    }

    /**
     * 测试用户不存在
     */
    @Test
    void testLoginWithUserNotFound() {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("notexist");
        loginRequest.setPassword("123456");

        // Mock AuthMapper 返回null（用户不存在）
        when(authMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginRequest);
        });

        assertEquals(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode(), exception.getCode());
        assertEquals(ResultCode.USERNAME_OR_PASSWORD_ERROR.getMessage(), exception.getMessage());
    }

    /**
     * 测试密码错误
     */
    @Test
    void testLoginWithWrongPassword() {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("wrongpassword");

        // Mock AuthMapper 返回用户
        when(authMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginRequest);
        });

        assertEquals(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode(), exception.getCode());
        assertEquals(ResultCode.USERNAME_OR_PASSWORD_ERROR.getMessage(), exception.getMessage());
    }

    /**
     * 测试权限配置错误
     */
    @Test
    void testLoginWithInvalidPermission() {
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("123456");

        // 创建权限无效的用户
        User invalidUser = new User();
        invalidUser.setId(1L);
        invalidUser.setUsername("admin");
        invalidUser.setSecret(passwordEncoder.encode("123456"));
        invalidUser.setPermission(999); // 无效的权限值

        when(authMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invalidUser);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(loginRequest);
        });

        assertEquals(ResultCode.FAIL.getCode(), exception.getCode());
        assertEquals("用户权限配置错误", exception.getMessage());
    }

    /**
     * 测试不同角色的登录
     */
    @Test
    void testLoginWithDifferentRoles() {
        // 测试顾客用户
        User customerUser = new User();
        customerUser.setId(2L);
        customerUser.setUsername("customer");
        customerUser.setSecret(passwordEncoder.encode("123456"));
        customerUser.setPermission(2); // 顾客权限

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("customer");
        loginRequest.setPassword("123456");

        when(authMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(customerUser);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("customer.token");
        when(jwtUtil.getExpiration()).thenReturn(7200L);

        LoginResponseDTO result = authService.login(loginRequest);

        assertNotNull(result);
        assertEquals("customer", result.getUserInfo().getRoleCode());
        assertEquals("顾客用户", result.getUserInfo().getRoleName());
    }

    /**
     * 生成并输出BCrypt哈希值
     * 用于生成数据库中的密码哈希值
     * 运行此测试后，将哈希值复制到数据库SQL文件中
     */
    @Test
    void generatePasswordHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "123456"; // 要加密的密码

        System.out.println("========================================");
        System.out.println("BCrypt 密码哈希值生成");
        System.out.println("========================================");
        System.out.println("原始密码: " + password);
        System.out.println();

        // 生成多个哈希值（每次生成的都不同，但都能验证通过）
        for (int i = 1; i <= 5; i++) {
            String hash = encoder.encode(password);
            System.out.println("哈希值 #" + i + ": " + hash);
            
            // 验证哈希值是否正确
            boolean matches = encoder.matches(password, hash);
            System.out.println("验证结果: " + (matches ? "✓ 正确" : "✗ 错误"));
            System.out.println();
        }

        System.out.println("========================================");
        System.out.println("提示：");
        System.out.println("1. 以上任意一个哈希值都可以用于数据库");
        System.out.println("2. 每次生成的哈希值都不同，但都能验证相同的密码");
        System.out.println("3. 将哈希值复制到 database/user.sql 文件中");
        System.out.println("========================================");
    }
}

