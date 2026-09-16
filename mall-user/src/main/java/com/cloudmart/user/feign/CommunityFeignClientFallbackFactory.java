package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * mall-community Feign 降级工厂。
 *
 * <p>隐私可见性采用 Fail-Closed 策略：社区服务不可用时，默认生日/邮箱均不可见，
 * 避免敏感信息在依赖故障时被泄露（安全 > 可用）。</p>
 */
@Slf4j
@Component
public class CommunityFeignClientFallbackFactory implements FallbackFactory<CommunityFeignClient> {

    @Override
    public CommunityFeignClient create(Throwable cause) {
        log.warn("mall-community Feign 降级，隐私字段默认隐藏: {}", cause.getMessage());
        return new CommunityFeignClient() {
            @Override
            public ApiResponse<Map<String, Object>> getPrivacyVisibility(Long targetUserId) {
                return ApiResponse.ok(Map.of("birthdayVisible", false, "emailVisible", false));
            }
        };
    }
}