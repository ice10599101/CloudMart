package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

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

            @Override
            public ApiResponse<Void> clearCheckedBySkus(Long userId, Map<String, Object> request) {
                // T03：精确清理失败仅告警（购物车残留不影响订单事实，用户可手动删）
                log.warn("精确清理购物车降级跳过, userId={}: {}", userId, cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
