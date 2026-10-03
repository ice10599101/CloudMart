package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ES 索引管理代理降级：真实根因必须透传给运营——索引重建失败静默成
 * "服务不可用"会掩盖蓝绿切换的精确失败点。
 */
@Slf4j
@Component
public class ProductEsFeignClientFallbackFactory implements FallbackFactory<ProductEsFeignClient> {

    @Override
    public ProductEsFeignClient create(Throwable cause) {
        log.error("ES 索引管理调用失败: {}", cause.getMessage());
        return new ProductEsFeignClient() {
            @Override
            public ApiResponse<Map<String, Object>> indexStatus() {
                throw new BusinessException("PRODUCT_ES_UNAVAILABLE", "商品搜索服务不可用: " + cause.getMessage());
            }

            @Override
            public ApiResponse<Map<String, Object>> indexVersions() {
                throw new BusinessException("PRODUCT_ES_UNAVAILABLE", "商品搜索服务不可用: " + cause.getMessage());
            }

            @Override
            public ApiResponse<Map<String, Object>> fullRebuild() {
                throw new BusinessException("PRODUCT_ES_UNAVAILABLE", "索引重建失败: " + cause.getMessage());
            }
        };
    }
}
