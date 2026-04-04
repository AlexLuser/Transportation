package com.fm.user.service;

import com.fm.common.dto.PageResult;
import com.fm.common.entity.User;

public interface UserService {
    User getUserByUsername(String username);
    User getUserById(Long id);

    /**
     * 管理员分页查询全部用户，支持按用户名关键词模糊搜索
     * @param current 页码（从1开始）
     * @param size    每页大小
     * @param keyword 用户名关键词（可为 null）
     * @return 分页结果
     */
    PageResult<User> getAllUsers(Long current, Long size, String keyword);
}
