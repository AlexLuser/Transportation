package com.fm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fm.common.dto.PageResult;
import com.fm.common.entity.User;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.user.dto.PendingUserDTO;
import com.fm.user.entity.DriverReview;
import com.fm.user.entity.ShopReview;
import com.fm.user.mapper.DriverReviewMapper;
import com.fm.user.mapper.ShopReviewMapper;
import com.fm.user.mapper.UserMapper;
import com.fm.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ShopReviewMapper shopReviewMapper;

    @Autowired
    private DriverReviewMapper driverReviewMapper;

    @Override
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public PageResult<User> getAllUsers(Long current, Long size, String keyword) {
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(User::getUsername, keyword);
        }
        wrapper.orderByAsc(User::getId);
        IPage<User> result = userMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    @Override
    public List<PendingUserDTO> getPendingUsers() {
        List<PendingUserDTO> result = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        // 查询所有待审核商户
        LambdaQueryWrapper<ShopReview> shopQuery = new LambdaQueryWrapper<>();
        shopQuery.eq(ShopReview::getStatus, 2);
        List<ShopReview> pendingShops = shopReviewMapper.selectList(shopQuery);
        for (ShopReview shop : pendingShops) {
            User user = userMapper.selectById(shop.getUserId());
            if (user == null) continue;
            PendingUserDTO dto = new PendingUserDTO();
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setRole("shop");
            dto.setDetailId(shop.getId());
            dto.setShopName(shop.getShopName());
            dto.setShopPhone(shop.getShopPhone());
            dto.setShopEmail(shop.getShopEmail());
            dto.setBusinessLicense(shop.getBusinessLicense());
            result.add(dto);
        }

        // 查询所有待审核司机
        LambdaQueryWrapper<DriverReview> driverQuery = new LambdaQueryWrapper<>();
        driverQuery.eq(DriverReview::getStatus, 2);
        List<DriverReview> pendingDrivers = driverReviewMapper.selectList(driverQuery);
        for (DriverReview driver : pendingDrivers) {
            User user = userMapper.selectById(driver.getUserId());
            if (user == null) continue;
            PendingUserDTO dto = new PendingUserDTO();
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setRole("driver");
            dto.setDetailId(driver.getId());
            dto.setRealName(driver.getRealName());
            dto.setPhone(driver.getPhone());
            dto.setLicenseNumber(driver.getLicenseNumber());
            dto.setLicenseType(driver.getLicenseType());
            if (driver.getLicenseExpireDate() != null) {
                dto.setLicenseExpireDate(sdf.format(driver.getLicenseExpireDate()));
            }
            result.add(dto);
        }

        return result;
    }

    @Override
    @Transactional
    public void reviewUser(Long userId, boolean approve) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "用户不存在");
        }

        if (user.getPermission() == 3) {
            LambdaQueryWrapper<ShopReview> query = new LambdaQueryWrapper<>();
            query.eq(ShopReview::getUserId, userId);
            ShopReview shop = shopReviewMapper.selectOne(query);
            if (shop == null) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "商户信息不存在");
            }
            if (approve) {
                LambdaUpdateWrapper<ShopReview> update = new LambdaUpdateWrapper<>();
                update.eq(ShopReview::getUserId, userId).set(ShopReview::getStatus, 1);
                shopReviewMapper.update(null, update);
            } else {
                shopReviewMapper.deleteById(shop.getId());
                userMapper.deleteById(userId);
            }
        } else if (user.getPermission() == 4) {
            LambdaQueryWrapper<DriverReview> query = new LambdaQueryWrapper<>();
            query.eq(DriverReview::getUserId, userId);
            DriverReview driver = driverReviewMapper.selectOne(query);
            if (driver == null) {
                throw new BusinessException(ResultCode.FAIL.getCode(), "司机信息不存在");
            }
            if (approve) {
                LambdaUpdateWrapper<DriverReview> update = new LambdaUpdateWrapper<>();
                update.eq(DriverReview::getUserId, userId).set(DriverReview::getStatus, 1);
                driverReviewMapper.update(null, update);
            } else {
                driverReviewMapper.deleteById(driver.getId());
                userMapper.deleteById(userId);
            }
        } else {
            throw new BusinessException(ResultCode.FAIL.getCode(), "该用户无需审核");
        }
    }
}
