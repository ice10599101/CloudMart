package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CartFeignClientFallbackFactory implements FallbackFactory<CartFeignClient> {

    @Override
    public CartFeignClient create(Throwable cause) {
        log.error("购物车服务调用失败: {}", cause.getMessage());
        return new CartFeignClient() {
            @Override
            public ApiResponse<Object> getCartByUserId(Long userId) {
                throw new BusinessException("CART_SERVICE_UNAVAILABLE", "购物车服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> removeCartItem(Long userId, Long skuId) {
                throw new BusinessException("CART_SERVICE_UNAVAILABLE", "购物车服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> clearCart(Long userId) {
                throw new BusinessException("CART_SERVICE_UNAVAILABLE", "购物车服务不可用，请稍后重试");
            }
        };
    }
}
