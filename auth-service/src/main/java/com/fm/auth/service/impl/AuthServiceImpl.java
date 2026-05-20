package com.fm.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fm.auth.entity.DriverRegistration;
import com.fm.auth.entity.ShopRegistration;
import com.fm.auth.mapper.AuthMapper;
import com.fm.auth.mapper.DriverRegistrationMapper;
import com.fm.auth.mapper.ShopRegistrationMapper;
import com.fm.auth.service.AuthService;
import com.fm.common.constant.PermissionEnum;
import com.fm.common.dto.LoginRequestDTO;
import com.fm.common.dto.LoginResponseDTO;
import com.fm.common.dto.RegisterRequestDTO;
import com.fm.common.dto.UserInfoDTO;
import com.fm.common.entity.User;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * 认证服务实现类
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private ShopRegistrationMapper shopRegistrationMapper;

    @Autowired
    private DriverRegistrationMapper driverRegistrationMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequest) {
        if (loginRequest.getUsername() == null || loginRequest.getUsername().trim().isEmpty()) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "用户名不能为空");
        }
        if (loginRequest.getPassword() == null || loginRequest.getPassword().trim().isEmpty()) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "密码不能为空");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, loginRequest.getUsername());
        User user = authMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getSecret())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        PermissionEnum permissionEnum = PermissionEnum.getByPermission(user.getPermission());
        if (permissionEnum == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "用户权限配置错误");
        }

        // 商户和司机需要检查审核状态
        if (user.getPermission() == 3) {
            LambdaQueryWrapper<ShopRegistration> shopQuery = new LambdaQueryWrapper<>();
            shopQuery.eq(ShopRegistration::getUserId, user.getId());
            ShopRegistration shop = shopRegistrationMapper.selectOne(shopQuery);
            if (shop != null && shop.getStatus() != null && shop.getStatus() == 2) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "账号待审核，请等待管理员审核通过后再登录");
            }
            if (shop != null && shop.getStatus() != null && shop.getStatus() == 0) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "账号已被禁用，请联系管理员");
            }
        }

        if (user.getPermission() == 4) {
            LambdaQueryWrapper<DriverRegistration> driverQuery = new LambdaQueryWrapper<>();
            driverQuery.eq(DriverRegistration::getUserId, user.getId());
            DriverRegistration driver = driverRegistrationMapper.selectOne(driverQuery);
            if (driver != null && driver.getStatus() != null && driver.getStatus() == 2) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "账号待审核，请等待管理员审核通过后再登录");
            }
            if (driver != null && driver.getStatus() != null && driver.getStatus() == 0) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "账号已被禁用，请联系管理员");
            }
        }

        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                permissionEnum.getRoleCode()
        );

        UserInfoDTO userInfo = new UserInfoDTO();
        userInfo.setUserId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRoleCode(permissionEnum.getRoleCode());
        userInfo.setRoleName(permissionEnum.getRoleName());

        LoginResponseDTO loginResponse = new LoginResponseDTO();
        loginResponse.setToken(token);
        loginResponse.setUserInfo(userInfo);
        loginResponse.setExpiration(jwtUtil.getExpiration());

        return loginResponse;
    }

    @Override
    public void logout() {
        // 无状态JWT，token由前端清除
    }

    @Override
    @Transactional
    public void register(RegisterRequestDTO req) {
        if (!StringUtils.hasText(req.getUsername())) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "用户名不能为空");
        }
        if (!StringUtils.hasText(req.getPassword())) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "密码不能为空");
        }
        if (!StringUtils.hasText(req.getRole())) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "角色不能为空");
        }

        // 用户名唯一校验
        LambdaQueryWrapper<User> existQuery = new LambdaQueryWrapper<>();
        existQuery.eq(User::getUsername, req.getUsername());
        if (authMapper.selectOne(existQuery) != null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "用户名已存在");
        }

        int permission;
        switch (req.getRole()) {
            case "customer" -> permission = 2;
            case "shop"     -> permission = 3;
            case "driver"   -> permission = 4;
            default -> throw new BusinessException(ResultCode.FAIL.getCode(), "不支持的角色类型");
        }

        // 创建用户
        User user = new User();
        user.setUsername(req.getUsername());
        user.setSecret(passwordEncoder.encode(req.getPassword()));
        user.setPermission(permission);
        authMapper.insert(user);

        // 根据角色创建对应的详情记录
        if (permission == 3) {
            if (!StringUtils.hasText(req.getShopName())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "商户名称不能为空");
            }
            if (!StringUtils.hasText(req.getBusinessLicense())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "营业执照号不能为空");
            }
            ShopRegistration shop = new ShopRegistration();
            shop.setUserId(user.getId());
            shop.setShopName(req.getShopName());
            shop.setShopPhone(req.getShopPhone());
            shop.setShopEmail(req.getShopEmail());
            shop.setBusinessLicense(req.getBusinessLicense());
            shop.setStatus(2); // 待审核
            shopRegistrationMapper.insert(shop);
        }

        if (permission == 4) {
            if (!StringUtils.hasText(req.getRealName())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "真实姓名不能为空");
            }
            if (!StringUtils.hasText(req.getLicenseNumber())) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "驾驶证号不能为空");
            }
            DriverRegistration driver = new DriverRegistration();
            driver.setUserId(user.getId());
            driver.setRealName(req.getRealName());
            driver.setPhone(req.getPhone());
            driver.setEmail(req.getEmail());
            driver.setLicenseNumber(req.getLicenseNumber());
            driver.setLicenseType(req.getLicenseType());
            if (StringUtils.hasText(req.getLicenseExpireDate())) {
                try {
                    driver.setLicenseExpireDate(
                            new SimpleDateFormat("yyyy-MM-dd").parse(req.getLicenseExpireDate()));
                } catch (ParseException e) {
                    throw new BusinessException(ResultCode.FAIL.getCode(), "驾驶证到期日期格式错误，应为 yyyy-MM-dd");
                }
            }
            driver.setStatus(2); // 待审核
            driverRegistrationMapper.insert(driver);
        }
        // 顾客直接注册通过，无需额外记录（顾客信息在 customer-service 中由用户自行完善）
    }
}
