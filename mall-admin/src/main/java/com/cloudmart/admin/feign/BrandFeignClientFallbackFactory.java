package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class BrandFeignClientFallbackFactory implements FallbackFactory<BrandFeignClient> {

    @Override
    public BrandFeignClient create(Throwable cause) {
        log.error("品牌服务调用失败: {}", cause.getMessage());
        return new BrandFeignClient() {
            @Override
            public ApiResponse<Object> listBrands(Map<String, Object> params) {
                throw new BusinessException("BRAND_SERVICE_UNAVAILABLE", "品牌服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> getBrand(Long id) {
                throw new BusinessException("BRAND_SERVICE_UNAVAILABLE", "品牌服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createBrand(Map<String, Object> body) {
                throw new BusinessException("BRAND_SERVICE_UNAVAILABLE", "品牌服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateBrand(Long id, Map<String, Object> body) {
                throw new BusinessException("BRAND_SERVICE_UNAVAILABLE", "品牌服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteBrand(Long id) {
                throw new BusinessException("BRAND_SERVICE_UNAVAILABLE", "品牌服务不可用，请稍后重试");
            }
        };
    }
}
