package com.cloudmart.auth.feign;

import com.cloudmart.auth.dto.AdminUserDTO;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.common.api.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class AdminUserFeignClientFallbackFactory implements FallbackFactory<AdminUserFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(AdminUserFeignClientFallbackFactory.class);

    private final ObjectMapper objectMapper;

    public AdminUserFeignClientFallbackFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AdminUserFeignClient create(Throwable cause) {
        log.warn("Admin user Feign call failed: {}", cause.getMessage());

        return new AdminUserFeignClient() {
            @Override
            public ApiResponse<AdminUserDTO> validateAdmin(ValidateRequest request) {
                return extractErrorResponse(cause);
            }

            @Override
            public ApiResponse<AdminUserDTO> getPermissionsByUserId(Long userId) {
                return extractErrorResponse(cause);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<AdminUserDTO> extractErrorResponse(Throwable cause) {
        if (cause instanceof FeignException feignException) {
            try {
                String body = feignException.contentUTF8();
                if (body != null && !body.isEmpty()) {
                    return (ApiResponse<AdminUserDTO>) objectMapper.readValue(body, ApiResponse.class);
                }
            } catch (Exception e) {
                log.warn("Failed to parse Feign error response: {}", e.getMessage());
            }

            int status = feignException.status();
            if (status == 401 || status == 403) {
                return ApiResponse.fail("AUTH_FAILED", "用户名或密码错误");
            }
        }

        return ApiResponse.fail("ADMIN_SERVICE_UNAVAILABLE", "管理员服务暂不可用");
    }
}
