package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(contextId = "cartFeignClient", name = "mall-cart", path = "/admin/cart", fallbackFactory = CartFeignClientFallbackFactory.class)
public interface CartFeignClient {

    @GetMapping("/{userId}")
    ApiResponse<Object> getCartByUserId(@PathVariable("userId") Long userId);

    @DeleteMapping("/{userId}/items/{skuId}")
    ApiResponse<Void> removeCartItem(@PathVariable("userId") Long userId, @PathVariable("skuId") Long skuId);

    @DeleteMapping("/{userId}")
    ApiResponse<Void> clearCart(@PathVariable("userId") Long userId);
}
