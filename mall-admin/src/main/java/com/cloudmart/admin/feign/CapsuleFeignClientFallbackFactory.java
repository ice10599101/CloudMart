package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.feign.FeignBusinessErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 时间胶囊管理端 Feign 降级工厂（Sprint 2.4 + R17）。
 * 业务错误（4xx 标准信封）透传原 code；网络/非信封故障折叠为服务不可用。
 */
@Component
@Slf4j
public class CapsuleFeignClientFallbackFactory implements FallbackFactory<CapsuleFeignClient> {

    private static final String FALLBACK_CODE = "CAPSULE_SERVICE_UNAVAILABLE";
    private static final String FALLBACK_MESSAGE = "时间胶囊服务不可用，请稍后重试";

    @Override
    public CapsuleFeignClient create(Throwable cause) {
        log.error("时间胶囊管理服务调用失败: {}", cause.getMessage());
        return new CapsuleFeignClient() {
            @Override
            public ApiResponse<Object> getStats() {
                throw FeignBusinessErrors.parse(cause, FALLBACK_CODE, FALLBACK_MESSAGE);
            }

            @Override
            public ApiResponse<Object> wallPending(int page, int size) {
                throw FeignBusinessErrors.parse(cause, FALLBACK_CODE, FALLBACK_MESSAGE);
            }

            @Override
            public ApiResponse<Object> wallApproved(int page, int size) {
                throw FeignBusinessErrors.parse(cause, FALLBACK_CODE, FALLBACK_MESSAGE);
            }

            @Override
            public ApiResponse<Object> decideWall(Long id, Map<String, Object> body) {
                throw FeignBusinessErrors.parse(cause, FALLBACK_CODE, FALLBACK_MESSAGE);
            }

            @Override
            public ApiResponse<Object> takeDownWall(Long id, Map<String, Object> body) {
                throw FeignBusinessErrors.parse(cause, FALLBACK_CODE, FALLBACK_MESSAGE);
            }
        };
    }
}
