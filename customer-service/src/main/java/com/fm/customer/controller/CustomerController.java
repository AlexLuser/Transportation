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
 * GET    /customers/{id}        - 获取个人信息
 * POST   /customers             - 添加个人信息
 * PUT    /customers             - 修改个人信息
 * DELETE /customers/{id}        - 删除个人信息
 */
@Tag(name = "顾客管理", description = "顾客信息相关接口")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    
    @Autowired
    private CustomerService customerService;
    
    /**
     * 获取个人信息
     * GET /customers/{id}
     */
    @Operation(summary = "获取个人信息", description = "根据个人ID获取个人信息")
    @GetMapping("/{id}")
    public Result<Customer> getCustomer(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "个人ID（customer_id）", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            return Result.error("个人信息不存在");
        }
        
        // 管理员可以访问所有用户信息，普通用户只能访问自己的信息
        if (!"admin".equals(roleCode)) {
            Customer currentUserCustomer = customerService.getCustomerByUserId(userId);
            if (currentUserCustomer == null || !currentUserCustomer.getId().equals(customer.getId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他用户的信息");
            }
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
        
        // 检查是否已存在
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
        
        // 获取现有记录
        Customer existingCustomer = customerService.getCustomerByUserId(userId);
        if (existingCustomer == null) {
            return Result.error("个人信息不存在，请使用POST方法创建");
        }
        
        // 更新现有记录
        customer.setId(existingCustomer.getId());
        customer.setUserId(userId);  // 确保userId不被修改
        customer = customerService.saveOrUpdateCustomer(customer);
        return Result.success(customer);
    }
    
    /**
     * 删除个人信息
     * DELETE /customers/{id}
     */
    @Operation(summary = "删除个人信息", description = "根据个人ID删除个人信息")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteCustomer(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "个人ID（customer_id）", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            return Result.error("个人信息不存在");
        }
        
        // 管理员可以删除所有用户信息，普通用户只能删除自己的信息
        if (!"admin".equals(roleCode)) {
            Customer currentUserCustomer = customerService.getCustomerByUserId(userId);
            if (currentUserCustomer == null || !currentUserCustomer.getId().equals(customer.getId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权删除其他用户的信息");
            }
        }
        
        // 删除顾客信息（注意：这里只删除顾客信息，关联的地址需要单独删除）
        boolean success = customerService.deleteCustomer(id);
        return Result.success(success);
    }
}
