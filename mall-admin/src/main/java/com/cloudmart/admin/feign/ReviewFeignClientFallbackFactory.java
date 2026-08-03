package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.ReviewSearchRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReviewFeignClientFallbackFactory implements FallbackFactory<ReviewFeignClient> {

    @Override
    public ReviewFeignClient create(Throwable cause) {
        log.error("商品服务调用失败: {}", cause.getMessage());
        return new ReviewFeignClient() {
            @Override
            public ApiResponse<Object> listReviews(ReviewSearchRequest request) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> getReview(Long id) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> updateReviewStatus(Long id, Integer status) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteReview(Long id) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> getReviewStats(Long productId) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }
        };
    }
}
