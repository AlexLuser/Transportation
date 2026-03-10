package com.fm.user.controller;

import com.fm.common.entity.User;
import com.fm.common.result.Result;
import com.fm.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 * RESTful风格接口
 */
@Tag(name = "用户管理", description = "用户相关接口")
@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;

    /**
     * 根据ID获取用户
     * RESTful: GET /api/users/{id}
     */
    @Operation(summary = "根据ID获取用户", description = "通过用户ID查询用户信息")
    @GetMapping("/{id}")
    public Result<User> getUserById(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }

    /**
     * 根据用户名查询用户
     * RESTful: GET /api/users/username/{username}
     */
    @Operation(summary = "根据用户名查询用户", description = "通过用户名查询用户信息")
    @GetMapping("/username/{username}")
    public Result<User> getUserByUsername(
            @Parameter(description = "用户名", required = true)
            @PathVariable String username) {
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }
}
