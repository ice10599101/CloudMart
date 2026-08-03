package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.*;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        log.error("商品服务调用失败", cause);
        return new ProductFeignClient() {
            @Override
            public ApiResponse<ProductSearchResultDTO> searchProducts(ProductSearchRequest request) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<ProductDTO> getProductById(Long id) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<ProductDTO> createProduct(CreateProductRequest request) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<ProductDTO> updateProduct(Long id, UpdateProductRequest request) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteProduct(Long id) {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<CountResponse> getProductCount() {
                throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }
        };
    }
}
