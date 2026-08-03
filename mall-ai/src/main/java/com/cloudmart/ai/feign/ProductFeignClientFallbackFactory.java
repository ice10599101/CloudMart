package com.cloudmart.ai.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        log.error("商品服务调用失败: {}", cause.getMessage());
        return new ProductFeignClient() {
            @Override
            public ApiResponse<Map<String, Object>> getProduct(Long id, String internalCall) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Map<String, Object>> getReviews(Long productId, int page, int size, String internalCall) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }
        };
    }
}
