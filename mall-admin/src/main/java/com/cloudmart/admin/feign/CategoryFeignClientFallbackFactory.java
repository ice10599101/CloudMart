package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.CategoryDTO;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class CategoryFeignClientFallbackFactory implements FallbackFactory<CategoryFeignClient> {

    @Override
    public CategoryFeignClient create(Throwable cause) {
        log.error("商品服务调用失败: {}", cause.getMessage());
        return new CategoryFeignClient() {
            @Override
            public ApiResponse<List<CategoryDTO>> listCategories() {
                throw FeignBusinessErrors.parse(cause, "PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<CategoryDTO> createCategory(String name, Long parentId) {
                throw FeignBusinessErrors.parse(cause, "PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<CategoryDTO> updateCategory(Long id, String name, Long parentId,
                                                            Integer sortOrder, Integer status) {
                throw FeignBusinessErrors.parse(cause, "PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteCategory(Long id) {
                throw FeignBusinessErrors.parse(cause, "PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
            }
        };
    }
}
