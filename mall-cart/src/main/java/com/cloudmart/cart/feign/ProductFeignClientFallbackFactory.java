package com.cloudmart.cart.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        log.error("商品服务调用失败: {}", cause.getMessage());
        return new ProductFeignClient() {
            @Override
            public ApiResponse<ProductInfo> getProductById(Long id) {
                log.warn("商品服务降级, productId={}: {}", id, cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
