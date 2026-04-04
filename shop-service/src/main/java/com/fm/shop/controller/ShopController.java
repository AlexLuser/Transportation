package com.fm.shop.controller;

import com.fm.common.exception.BusinessException;
import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.shop.entity.Shop;
import com.fm.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 商户管理Controller
 * 提供商户信息的RESTful API接口
 * 接口设计：
 * GET    /api/shops/{id}    - 获取商户信息
 * POST   /api/shops         - 添加商户信息
 * PUT    /api/shops         - 修改商户信息
 * DELETE /api/shops/{id}    - 删除商户信息
 */
@Tag(name = "商户管理", description = "商户信息相关接口")
@RestController
@RequestMapping("/api/shops")
public class ShopController {
    
    @Autowired
    private ShopService shopService;
    
    /**
     * 获取商户信息
     * @param userIdHeader 用户ID（从请求头获取）
     * @param roleCode 角色代码（从请求头获取）
     * @param id 商户ID
     * @return 商户信息
     */
    @Operation(summary = "获取商户信息", description = "根据商户ID获取商户信息")
    @GetMapping("/{id}")
    public Result<Shop> getShop(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "商户ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);

        Shop shop = shopService.getShopById(id);
        if (shop == null) {
            return Result.error("商户信息不存在");
        }
        
        // 管理员可以访问所有商户信息，普通商户只能访问自己的信息
        if (!"admin".equals(roleCode)) {
            Shop currentUserShop = shopService.getShopByUserId(userId);
            if (currentUserShop == null || !currentUserShop.getId().equals(shop.getId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他商户的信息");
            }
        }
        
        return Result.success(shop);
    }
    
    /**
     * 添加商户信息
     * @param userIdHeader 用户ID（从请求头获取）
     * @param shop 商户信息
     * @return 添加后的商户信息
     */
    @Operation(summary = "添加商户信息", description = "添加新的商户信息")
    @PostMapping
    public Result<Shop> addShop(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Shop shop) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Shop existingShop = shopService.getShopByUserId(userId);
        if (existingShop != null) {
            return Result.error("商户信息已存在，请使用PUT方法更新");
        }
        
        shop.setUserId(userId);
        shop = shopService.saveOrUpdateShop(shop);
        return Result.success(shop);
    }
    
    /**
     * 修改商户信息
     * @param userIdHeader 用户ID（从请求头获取）
     * @param shop 商户信息
     * @return 修改后的商户信息
     */
    @Operation(summary = "修改商户信息", description = "修改商户信息")
    @PutMapping
    public Result<Shop> updateShop(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestBody Shop shop) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Shop existingShop = shopService.getShopByUserId(userId);
        if (existingShop == null) {
            return Result.error("商户信息不存在，请使用POST方法创建");
        }
        
        shop.setId(existingShop.getId());
        shop.setUserId(userId);
        shop = shopService.saveOrUpdateShop(shop);
        return Result.success(shop);
    }
    
    /**
     * 【内部接口】根据 userId 查询商户信息
     * GET /api/shops/internal/user/{userId}
     * 仅供服务间调用，根据 user.id 返回商户业务主体信息
     */
    @Operation(summary = "内部：根据userId查询商户", description = "服务间内部调用，根据 user.id 返回商户业务主体信息")
    @GetMapping("/internal/user/{userId}")
    public Result<Shop> getShopByUserId(
            @Parameter(description = "用户ID（user.id）", required = true)
            @PathVariable Long userId) {
        Shop shop = shopService.getShopByUserId(userId);
        if (shop == null) {
            return Result.error("商户信息不存在");
        }
        return Result.success(shop);
    }

    /**
     * 删除商户信息
     * @param userIdHeader 用户ID（从请求头获取）
     * @param roleCode 角色代码（从请求头获取）
     * @param id 商户ID
     * @return 是否删除成功
     */
    @Operation(summary = "删除商户信息", description = "根据商户ID删除商户信息")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteShop(
            @RequestHeader(value = "userId", required = false) String userIdHeader,
            @RequestHeader(value = "roleCode", required = false) String roleCode,
            @Parameter(description = "商户ID", required = true)
            @PathVariable Long id) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdHeader);
        
        Shop shop = shopService.getShopById(id);
        if (shop == null) {
            return Result.error("商户信息不存在");
        }
        
        // 管理员可以删除所有商户信息，普通商户只能删除自己的信息
        if (!"admin".equals(roleCode)) {
            Shop currentUserShop = shopService.getShopByUserId(userId);
            if (currentUserShop == null || !currentUserShop.getId().equals(shop.getId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权删除其他商户的信息");
            }
        }
        
        boolean success = shopService.deleteShop(id);
        return Result.success(success);
    }
}

