package com.cloudmart.auth.feign;

import com.cloudmart.auth.dto.UserDTO;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    private final ObjectMapper objectMapper;

    public UserFeignClientFallbackFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("用户服务调用失败: {}", cause.getMessage());

        return new UserFeignClient() {
            @Override
            public ApiResponse<UserDTO> validateUser(ValidateRequest request) {
                if (cause instanceof FeignException feignException) {
                    int status = feignException.status();
                    if (status >= 400 && status < 500) {
                        return ApiResponse.fail("AUTH_FAILED", "账号或密码错误");
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
