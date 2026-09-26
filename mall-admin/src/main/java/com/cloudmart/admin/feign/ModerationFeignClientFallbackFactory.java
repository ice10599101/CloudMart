package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 治理工单客户端降级：管理操作不可静默失败——抛 WISH_SERVICE_UNAVAILABLE（503），
 * 由前端提示稍后重试。
 */
@Slf4j
@Component
public class ModerationFeignClientFallbackFactory implements FallbackFactory<ModerationFeignClient> {

    @Override
    public ModerationFeignClient create(Throwable cause) {
        return new ModerationFeignClient() {
            @Override
            public ApiResponse<List<Map<String, Object>>> listCases(String status, Long cursor, Integer pageSize) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Long> decide(Long caseId, Map<String, Object> body, Long actorId) {
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Void> resolveAppeal(Long appealId, Map<String, Object> body, Long reviewerId) {
                throw unavailable(cause);
            }

            private com.cloudmart.common.exception.BusinessException unavailable(Throwable cause) {
                log.warn("治理工单调用 mall-wish 失败: {}", cause.getMessage());
                return new com.cloudmart.common.exception.BusinessException(
                        "WISH_SERVICE_UNAVAILABLE", "心愿治理服务暂时不可用，请稍后重试", cause);
            }
        };
    }
}
