package com.cloudmart.cart.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.cart.converter.CartConverter;
import com.cloudmart.cart.dto.CartDTO;
import com.cloudmart.cart.service.CartService;
import com.cloudmart.cart.vo.CartVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/cart")
@Tag(name = "购物车管理(后台)", description = "管理后台购物车管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminCartController {

    private final CartService cartService;
    private final CartConverter cartConverter;

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询用户购物车", description = "管理后台查询指定用户的购物车")
    public ApiResponse<CartVO> getCartByUserId(
            @Parameter(description = "用户ID") @PathVariable("userId") Long userId) {
        CartDTO dto = cartService.getCart(userId);
        return ApiResponse.ok(cartConverter.cartDtoToVOWithItems(dto));
    }

    @DeleteMapping("/{userId}/items/{skuId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除购物车商品", description = "管理后台删除用户购物车中的指定商品")
    public ApiResponse<Void> removeCartItem(
            @Parameter(description = "用户ID") @PathVariable("userId") Long userId,
            @Parameter(description = "SKU ID") @PathVariable("skuId") Long skuId) {
        cartService.removeItem(userId, skuId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "清空用户购物车", description = "管理后台清空指定用户的购物车")
    public ApiResponse<Void> clearCart(
            @Parameter(description = "用户ID") @PathVariable("userId") Long userId) {
        cartService.clearCart(userId);
        return ApiResponse.ok(null);
    }
}
