package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.cloud.openfeign.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/** T15：邀请台账 Feign 降级（错误透传不吞） */
@Slf4j
@Component
public class InviteFeignClientFallbackFactory implements FallbackFactory<InviteFeignClient> {

    @Override
    public InviteFeignClient create(Throwable cause) {
        return new InviteFeignClient() {
            @Override
            public ApiResponse<Object> relations(String rewardStatus, int page, int size) {
                log.warn("[T15] 邀请台账查询降级: {}", cause.getMessage());
                throw unavailable(cause);
            }

            @Override
            public ApiResponse<Object> releaseHold(Long relationId, Map<String, String> body) {
                log.warn("[T15] HOLD 复核降级: {}", cause.getMessage());
                throw unavailable(cause);
            }

            private BusinessException unavailable(Throwable root) {
                return new BusinessException("USER_SERVICE_UNAVAILABLE",
                        "用户服务暂不可用，请稍后重试", root);
            }
        };
    }
}
