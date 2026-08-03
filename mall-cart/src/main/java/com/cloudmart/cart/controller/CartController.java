package com.cloudmart.cart.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.cart.converter.CartConverter;
import com.cloudmart.cart.dto.AddCartItemRequest;
import com.cloudmart.cart.dto.CartDTO;
import com.cloudmart.cart.dto.CartItemDTO;
import com.cloudmart.cart.dto.UpdateCartItemRequest;
import com.cloudmart.cart.service.CartService;
import com.cloudmart.cart.vo.CartItemVO;
import com.cloudmart.cart.vo.CartVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "购物车管理", description = "购物车的增删改查接口，基于Redis + DB双写")
public class CartController {

    private final CartService cartService;
    private final CartConverter cartConverter;

    public CartController(CartService cartService, CartConverter cartConverter) {
        this.cartService = cartService;
        this.cartConverter = cartConverter;
    }

    @GetMapping
    @Operation(summary = "查询购物车", description = "查询当前用户的购物车列表")
    public ApiResponse<CartVO> getCart(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        CartDTO dto = cartService.getCart(userId);
        return ApiResponse.ok(cartConverter.cartDtoToVOWithItems(dto));
    }

    @PostMapping("/items")
    @Operation(summary = "添加商品到购物车", description = "添加商品到购物车，若已存在则累加数量")
    public ApiResponse<CartItemVO> addItem(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody AddCartItemRequest request) {
        CartItemDTO dto = cartService.addItem(userId, request);
        return ApiResponse.ok(cartConverter.cartItemDtoToVO(dto));
    }

    @PutMapping("/items/{skuId}")
    @Operation(summary = "更新购物车项", description = "更新购物车项的数量和选中状态")
    public ApiResponse<CartItemVO> updateItem(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "SKU ID") @PathVariable("skuId") Long skuId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        CartItemDTO dto = cartService.updateItem(userId, skuId, request);
        return ApiResponse.ok(cartConverter.cartItemDtoToVO(dto));
    }

    @DeleteMapping("/items/{skuId}")
    @Operation(summary = "删除购物车项", description = "从购物车中移除指定SKU的商品")
    public ApiResponse<Void> removeItem(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "SKU ID") @PathVariable("skuId") Long skuId) {
        cartService.removeItem(userId, skuId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    @Operation(summary = "清空购物车", description = "清空当前用户的购物车")
    public ApiResponse<Void> clearCart(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        cartService.clearCart(userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/checked")
    @Operation(summary = "删除已选中商品", description = "删除购物车中已选中的商品（结算后调用）")
    public ApiResponse<Void> clearCheckedItems(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        cartService.clearCheckedItems(userId);
        return ApiResponse.ok(null);
    }
}
