package com.cloudmart.community.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    private final ObjectMapper objectMapper;

    public UserFeignClientFallbackFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("社区模块调用用户服务失败: {}", cause.getMessage());

        return new UserFeignClient() {
            @Override
            public ApiResponse<Map<String, Object>> getUserById(Long id) {
                if (cause instanceof FeignException feignException) {
                    int status = feignException.status();
                    if (status >= 400 && status < 500) {
                        return ApiResponse.fail("USER_SERVICE_ERROR", "用户服务请求失败");
                    }
                }
                throw extractBusinessException(cause);
            }

            @Override
            public ApiResponse<List<Map<String, Object>>> batchGetUsers(List<Long> ids) {
                if (cause instanceof FeignException feignException) {
                    int status = feignException.status();
                    if (status >= 400 && status < 500) {
                        return ApiResponse.fail("USER_SERVICE_ERROR", "用户服务请求失败");
                    }
                }
                throw extractBusinessException(cause);
            }

            @Override
            public ApiResponse<List<Map<String, Object>>> searchUsers(String keyword, int page, int pageSize) {
                if (cause instanceof FeignException feignException) {
                    int status = feignException.status();
                    if (status >= 400 && status < 500) {
                        return ApiResponse.fail("USER_SERVICE_ERROR", "用户服务请求失败");
                    }
                }
                throw extractBusinessException(cause);
            }
        };
    }

    private BusinessException extractBusinessException(Throwable cause) {
        if (cause instanceof FeignException feignException) {
            try {
                String body = feignException.contentUTF8();
                if (body != null && !body.isEmpty()) {
                    ApiResponse<?> response = objectMapper.readValue(body, ApiResponse.class);
                    if (response.error() != null) {
                        return new BusinessException(response.error().code(), response.error().message(), cause);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 Feign 错误响应失败: {}", e.getMessage());
            }
        }
        return new BusinessException("USER_SERVICE_UNAVAILABLE", "用户服务不可用，请稍后重试", cause);
    }
}
