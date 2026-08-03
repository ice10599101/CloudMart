package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.product.dto.WishlistDTO;
import com.cloudmart.product.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/wishlists")
@Tag(name = "商品收藏", description = "商品收藏/取消收藏接口")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping("/{productId}")
    @Operation(summary = "添加收藏", description = "将商品添加到收藏列表")
    public ApiResponse<Void> addToList(
            @Parameter(description = "商品ID", required = true) @PathVariable("productId") Long productId) {
        Long userId = getCurrentUserId();
        wishlistService.addToList(userId, productId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "取消收藏", description = "将商品从收藏列表移除")
    public ApiResponse<Void> removeFromList(
            @Parameter(description = "商品ID", required = true) @PathVariable("productId") Long productId) {
        Long userId = getCurrentUserId();
        wishlistService.removeFromList(userId, productId);
        return ApiResponse.ok(null);
    }

    @GetMapping
    @Operation(summary = "收藏列表", description = "分页查询当前用户的收藏列表（含商品信息）")
    public ApiResponse<java.util.List<WishlistDTO>> getUserWishlist(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        Long userId = getCurrentUserId();
        Page<WishlistDTO> result = wishlistService.getUserWishlist(userId, page, size);
        Meta meta = new Meta((int) result.getCurrent(), (int) result.getSize(), result.getTotal());
        return ApiResponse.ok(result.getRecords(), meta);
    }

    @GetMapping("/check/{productId}")
    @Operation(summary = "检查收藏状态", description = "检查商品是否在当前用户的收藏列表中")
    public ApiResponse<Map<String, Boolean>> checkWishlist(
            @Parameter(description = "商品ID", required = true) @PathVariable("productId") Long productId) {
        Long userId = getCurrentUserId();
        boolean inWishlist = wishlistService.isInWishlist(userId, productId);
        return ApiResponse.ok(Map.of("isInWishlist", inWishlist));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new com.cloudmart.common.exception.BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String principalStr) {
            try {
                return Long.parseLong(principalStr);
            } catch (NumberFormatException e) {
                throw new com.cloudmart.common.exception.BusinessException("UNAUTHORIZED", "内部服务调用缺少用户标识");
            }
        }
        throw new com.cloudmart.common.exception.BusinessException("UNAUTHORIZED", "无法获取用户信息");
    }
}
