package com.fm.user.service;

import com.fm.common.entity.User;

public interface UserService {
    User getUserByUsername(String username);
    User getUserById(Long id);
}
