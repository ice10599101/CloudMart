package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
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
            public ApiResponse<Void> clearCheckedItems(Long userId) {
                log.warn("清空购物车降级跳过, userId={}: {}", userId, cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
