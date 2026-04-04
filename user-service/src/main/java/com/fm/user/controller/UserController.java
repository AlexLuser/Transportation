package com.fm.user.controller;

import com.fm.common.dto.PageResult;
import com.fm.common.entity.User;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
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
     * 管理员分页查询全部用户，支持用户名关键词搜索
     * RESTful: GET /api/users
     */
    @Operation(summary = "分页查询用户列表", description = "仅管理员可用；返回全部用户列表，支持按用户名关键词模糊搜索")
    @GetMapping
    public Result<PageResult<User>> getUsers(
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "15") Long size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问");
        }
        return Result.success(userService.getAllUsers(current, size, keyword));
    }

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
