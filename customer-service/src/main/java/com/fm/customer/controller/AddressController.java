package com.fm.customer.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.customer.entity.Address;
import com.fm.customer.entity.Customer;
import com.fm.customer.service.AddressService;
import com.fm.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 地址管理Controller
 * 接口设计：
 * GET    /customers/{userId}/address   - 获取个人地址（路径参数为 userId，内部转换）
 * POST   /customers/address            - 添加个人地址
 * PUT    /customers/address            - 修改个人地址（isDefault=1 时服务层自动处理默认逻辑）
 * DELETE /customers/address/{id}       - 删除个人地址
 */
@Tag(name = "地址管理", description = "收货地址相关接口")
@RestController
@RequestMapping("/api/customers")
public class AddressController {

    @Autowired
    private AddressService addressService;

    @Autowired
    private CustomerService customerService;

    /**
     * 获取个人地址列表
     * GET /customers/{userId}/address
     * 路径参数传入 userId，内部通过 getCustomerByUserId 转换为 customerId
     */
    @Operation(summary = "获取个人地址", description = "路径参数为 userId，内部自动转换为 customerId 查询")
    @GetMapping("/{userId}/address")
    public Result<List<Address>> getAddresses(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "用户ID（user.id）", required = true)
            @PathVariable Long userId) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 普通用户只能查自己的地址
        if (!"admin".equals(roleCode) && !userId.equals(Long.parseLong(userIdHeader))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他用户的地址");
        }
        // 内部通过 userId 转换为 customerId
        Customer customer = customerService.getCustomerByUserId(userId);
        if (customer == null) {
            return Result.success(List.of());
        }
        return Result.success(addressService.getAddressesByCustomerId(customer.getId()));
    }

    /**
     * 添加个人地址
     * POST /customers/address
     */
    @Operation(summary = "添加个人地址", description = "添加新的个人地址")
    @PostMapping("/address")
    public Result<Address> addAddress(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Address address) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);

        Customer customer = customerService.getCustomerByUserId(userId);
        if (customer == null) {
            return Result.error("顾客信息不存在，请先完善个人信息");
        }

        address.setCustomerId(customer.getId());
        address = addressService.addAddress(address);
        return Result.success(address);
    }

    /**
     * 修改个人地址
     * PUT /customers/address
     * 请求体中携带 isDefault=1 时，服务层会自动处理"取消其他默认"逻辑
     */
    @Operation(summary = "修改个人地址", description = "修改个人地址；isDefault=1 时服务层自动将其设为唯一默认地址")
    @PutMapping("/address")
    public Result<Address> updateAddress(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Address address) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);

        if (address.getId() == null) {
            return Result.error("地址ID不能为空");
        }

        Address existingAddress = addressService.getAddressById(address.getId());
        if (existingAddress == null) {
            return Result.error("地址不存在");
        }

        Customer customer = customerService.getCustomerByUserId(userId);
        if (customer == null || !existingAddress.getCustomerId().equals(customer.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        address.setCustomerId(customer.getId());
        address = addressService.updateAddress(address);
        return Result.success(address);
    }

    /**
     * 删除个人地址
     * DELETE /customers/address/{id}
     */
    @Operation(summary = "删除个人地址", description = "根据地址ID删除个人地址")
    @DeleteMapping("/address/{id}")
    public Result<Boolean> deleteAddress(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @Parameter(description = "地址ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);

        Address existingAddress = addressService.getAddressById(id);
        if (existingAddress == null) {
            return Result.error("地址不存在");
        }

        Customer customer = customerService.getCustomerByUserId(userId);
        if (customer == null || !existingAddress.getCustomerId().equals(customer.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        boolean success = addressService.deleteAddress(id);
        return Result.success(success);
    }

    /**
     * 根据地址ID获取地址信息（内部服务调用，用于订单服务创建配送记录）
     * GET /customers/address/{addressId}
     */
    @Operation(summary = "根据地址ID获取地址信息", description = "根据地址ID获取地址信息（内部服务调用）")
    @GetMapping("/address/{addressId}")
    public Result<Address> getAddressById(
            @Parameter(description = "地址ID", required = true)
            @PathVariable Long addressId) {
        Address address = addressService.getAddressById(addressId);
        if (address == null) {
            return Result.error("地址不存在");
        }
        return Result.success(address);
    }
}
