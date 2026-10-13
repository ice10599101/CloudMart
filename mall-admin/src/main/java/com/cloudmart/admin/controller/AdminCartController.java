package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.CartFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务管理-购物车域：管理员代查/清理用户购物车。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：购物车")
public class AdminCartController {

    private final CartFeignClient cartFeignClient;

    public AdminCartController(CartFeignClient cartFeignClient) {
        this.cartFeignClient = cartFeignClient;
    }

    // ==================== 购物车 ====================

    @GetMapping("/carts/{userId}")
    @RequiresPermission("business:cart:query")
    @Operation(summary = "查看用户购物车", description = "管理员查看指定用户的购物车")
    public ApiResponse<Object> getCartByUserId(@PathVariable Long userId) {
        return cartFeignClient.getCartByUserId(userId);
    }

    @DeleteMapping("/carts/{userId}/items/{skuId}")
    @OperLog(title = "购物车管理", businessType = 3)
    @RequiresPermission("business:cart:edit")
    @Operation(summary = "删除购物车项", description = "管理员删除用户购物车中的指定商品")
    public ApiResponse<Void> removeCartItem(@PathVariable Long userId, @PathVariable Long skuId) {
        return cartFeignClient.removeCartItem(userId, skuId);
    }

    @DeleteMapping("/carts/{userId}/clear")
    @OperLog(title = "购物车管理", businessType = 9)
    @RequiresPermission("business:cart:edit")
    @Operation(summary = "清空购物车", description = "管理员清空指定用户的购物车")
    public ApiResponse<Void> clearCart(@PathVariable Long userId) {
        return cartFeignClient.clearCart(userId);
    }
}
