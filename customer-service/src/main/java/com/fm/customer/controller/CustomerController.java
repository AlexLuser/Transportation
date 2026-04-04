package com.fm.customer.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.customer.entity.Customer;
import com.fm.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 顾客信息管理Controller
 * 接口设计：
 * GET    /customers/{userId}     - 获取个人信息（路径参数为 userId，内部转换为 customerId）
 * POST   /customers              - 添加个人信息
 * PUT    /customers              - 修改个人信息
 * DELETE /customers/{userId}     - 删除个人信息
 */
@Tag(name = "顾客管理", description = "顾客信息相关接口")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    /**
     * 获取个人信息
     * GET /customers/{userId}
     * 路径参数传入 userId，内部通过 getCustomerByUserId 转换为 customerId
     */
    @Operation(summary = "获取个人信息", description = "路径参数为 userId，内部自动转换为 customerId 查询")
    @GetMapping("/{userId}")
    public Result<Customer> getCustomer(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "用户ID（user.id）", required = true)
            @PathVariable Long userId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 普通用户只能查自己
        if (!"admin".equals(roleCode) && !userId.equals(Long.parseLong(userIdHeader))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他用户的信息");
        }
        // 内部通过 userId 转换为顾客实体
        Customer customer = customerService.getCustomerByUserId(userId);
        if (customer == null) {
            return Result.error("个人信息不存在");
        }
        return Result.success(customer);
    }

    /**
     * 添加个人信息
     * POST /customers
     */
    @Operation(summary = "添加个人信息", description = "添加新的个人信息")
    @PostMapping
    public Result<Customer> addCustomer(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Customer customer) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);

        Customer existingCustomer = customerService.getCustomerByUserId(userId);
        if (existingCustomer != null) {
            return Result.error("个人信息已存在，请使用PUT方法更新");
        }

        customer.setUserId(userId);
        customer = customerService.saveOrUpdateCustomer(customer);
        return Result.success(customer);
    }

    /**
     * 修改个人信息
     * PUT /customers
     */
    @Operation(summary = "修改个人信息", description = "修改个人信息")
    @PutMapping
    public Result<Customer> updateCustomer(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Customer customer) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);

        Customer existingCustomer = customerService.getCustomerByUserId(userId);
        if (existingCustomer == null) {
            return Result.error("个人信息不存在，请使用POST方法创建");
        }

        customer.setId(existingCustomer.getId());
        customer.setUserId(userId);
        customer = customerService.saveOrUpdateCustomer(customer);
        return Result.success(customer);
    }

    /**
     * 【内部接口】根据 userId 查询顾客信息
     * GET /customers/internal/user/{userId}
     */
    @Operation(summary = "内部：根据userId查询顾客", description = "服务间内部调用，根据 user.id 返回顾客业务主体信息")
    @GetMapping("/internal/user/{userId}")
    public Result<Customer> getCustomerByUserId(
            @Parameter(description = "用户ID（user.id）", required = true)
            @PathVariable Long userId) {
        Customer customer = customerService.getCustomerByUserId(userId);
        if (customer == null) {
            return Result.error("顾客信息不存在");
        }
        return Result.success(customer);
    }

    /**
     * 删除个人信息
     * DELETE /customers/{userId}
     * 路径参数为 userId，内部转换为 customerId
     */
    @Operation(summary = "删除个人信息", description = "根据 userId 删除个人信息")
    @DeleteMapping("/{userId}")
    public Result<Boolean> deleteCustomer(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "用户ID（user.id）", required = true)
            @PathVariable Long userId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!"admin".equals(roleCode) && !userId.equals(Long.parseLong(userIdHeader))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除其他用户的信息");
        }

        Customer customer = customerService.getCustomerByUserId(userId);
        if (customer == null) {
            return Result.error("个人信息不存在");
        }

        boolean success = customerService.deleteCustomer(customer.getId());
        return Result.success(success);
    }
}
