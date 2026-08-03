package com.cloudmart.auth.feign;

import com.cloudmart.auth.dto.LoginLogRecordRequest;
import com.cloudmart.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class AdminLoginLogFeignClientFallbackFactory implements FallbackFactory<AdminLoginLogFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginLogFeignClientFallbackFactory.class);

    @Override
    public AdminLoginLogFeignClient create(Throwable cause) {
        log.warn("Login log Feign call failed: {}", cause.getMessage());
        return new AdminLoginLogFeignClient() {
            @Override
            public ApiResponse<Void> recordLogin(LoginLogRecordRequest request) {
                return ApiResponse.ok(null);
            }
        };
    }
}
