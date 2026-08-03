package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class AiFeignClientFallbackFactory implements FallbackFactory<AiFeignClient> {

    @Override
    public AiFeignClient create(Throwable cause) {
        log.error("AI 服务调用失败: {}", cause.getMessage());
        return new AiFeignClient() {
            @Override
            public ApiResponse<Map<String, Object>> chat(Long userId, Map<String, String> request) {
                throw new BusinessException("AI_SERVICE_UNAVAILABLE", "AI 服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Map<String, Object>> search(Long userId, String query) {
                throw new BusinessException("AI_SERVICE_UNAVAILABLE", "AI 服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> triggerFullSync() {
                throw new BusinessException("AI_SERVICE_UNAVAILABLE", "AI 服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> syncProduct(Long productId) {
                throw new BusinessException("AI_SERVICE_UNAVAILABLE", "AI 服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteProductVector(Long productId) {
                throw new BusinessException("AI_SERVICE_UNAVAILABLE", "AI 服务不可用，请稍后重试");
            }
        };
    }
}
