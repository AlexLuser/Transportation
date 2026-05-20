package com.fm.user.controller;

import com.fm.common.dto.PageResult;
import com.fm.common.entity.User;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.user.dto.PendingUserDTO;
import com.fm.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 */
@Tag(name = "用户管理", description = "用户相关接口")
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 管理员分页查询全部用户
     * GET /api/users
     */
    @Operation(summary = "分页查询用户列表")
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
     * GET /api/users/{id}
     */
    @Operation(summary = "根据ID获取用户")
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
     * GET /api/users/username/{username}
     */
    @Operation(summary = "根据用户名查询用户")
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

    /**
     * 管理员查询所有待审核用户（商户 + 司机）
     * GET /api/users/pending
     */
    @Operation(summary = "查询待审核用户列表", description = "仅管理员可用")
    @GetMapping("/pending")
    public Result<List<PendingUserDTO>> getPendingUsers(
            @RequestHeader(value = "roleCode", required = false) String roleCode) {
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问");
        }
        return Result.success(userService.getPendingUsers());
    }

    /**
     * 管理员审核用户（通过 / 拒绝）
     * PUT /api/users/{userId}/review
     * body: { "approve": true/false }
     */
    @Operation(summary = "审核用户", description = "仅管理员可用；approve=true 通过，false 拒绝并删除")
    @PutMapping("/{userId}/review")
    public Result<Void> reviewUser(
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @PathVariable Long userId,
            @RequestBody Map<String, Boolean> body) {
        if (!"admin".equals(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问");
        }
        Boolean approve = body.get("approve");
        if (approve == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "缺少 approve 参数");
        }
        userService.reviewUser(userId, approve);
        return Result.success();
    }
}
