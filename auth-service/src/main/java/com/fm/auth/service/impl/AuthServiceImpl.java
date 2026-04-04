package com.fm.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fm.auth.mapper.AuthMapper;
import com.fm.auth.service.AuthService;
import com.fm.common.constant.PermissionEnum;
import com.fm.common.dto.LoginRequestDTO;
import com.fm.common.dto.LoginResponseDTO;
import com.fm.common.dto.UserInfoDTO;
import com.fm.common.entity.User;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现类
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequest) {
        // 1. 参数校验
        if (loginRequest.getUsername() == null || loginRequest.getUsername().trim().isEmpty()) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "用户名不能为空");
        }
        if (loginRequest.getPassword() == null || loginRequest.getPassword().trim().isEmpty()) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "密码不能为空");
        }

        // 2. 查询用户（包含密码和权限）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, loginRequest.getUsername());
        User user = authMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 3. 验证密码
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getSecret())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 4. 根据permission获取角色信息
        PermissionEnum permissionEnum = PermissionEnum.getByPermission(user.getPermission());
        if (permissionEnum == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "用户权限配置错误");
        }

        // 5. 生成JWT Token
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                permissionEnum.getRoleCode()
        );

        // 6. 构建用户信息
        UserInfoDTO userInfo = new UserInfoDTO();
        userInfo.setUserId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRoleCode(permissionEnum.getRoleCode());
        userInfo.setRoleName(permissionEnum.getRoleName());

        // 7. 构建登录响应
        LoginResponseDTO loginResponse = new LoginResponseDTO();
        loginResponse.setToken(token);
        loginResponse.setUserInfo(userInfo);
        loginResponse.setExpiration(jwtUtil.getExpiration());

        return loginResponse;
    }

    @Override
    public void logout() {
        // 无状态JWT，token由前端清除，服务端无需额外操作
    }
}
