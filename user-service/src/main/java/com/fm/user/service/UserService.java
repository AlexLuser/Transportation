package com.fm.user.service;

import com.fm.common.dto.PageResult;
import com.fm.common.entity.User;
import com.fm.user.dto.PendingUserDTO;

import java.util.List;

public interface UserService {
    User getUserByUsername(String username);
    User getUserById(Long id);

    /**
     * 管理员分页查询全部用户，支持按用户名关键词模糊搜索
     */
    PageResult<User> getAllUsers(Long current, Long size, String keyword);

    /**
     * 查询所有待审核用户（商户 + 司机）
     */
    List<PendingUserDTO> getPendingUsers();

    /**
     * 审核用户：approve=true 通过（status→1），approve=false 拒绝（删除user及详情）
     */
    void reviewUser(Long userId, boolean approve);
}
