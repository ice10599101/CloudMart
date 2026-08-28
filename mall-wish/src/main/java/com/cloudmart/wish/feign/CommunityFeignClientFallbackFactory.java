package com.cloudmart.wish.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * mall-community Feign 降级工厂（Sprint 2.7 内容流转）。
 *
 * <p>community 不可用时返回失败信封：内容流转服务记 FAILED 日志并重试，
 * 还愿主链路不受影响（文档 2.7 验收：community 不可用时还愿仍成功）。</p>
 */
@Slf4j
@Component
public class CommunityFeignClientFallbackFactory implements FallbackFactory<CommunityFeignClient> {

    @Override
    public CommunityFeignClient create(Throwable cause) {
        log.warn("mall-community Feign 降级: {}", cause.getMessage());
        return new CommunityFeignClient() {

            @Override
            public ApiResponse<Map<String, Object>> createLegacyPost(Map<String, Object> body) {
                return ApiResponse.fail("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> hideLegacyPost(Long postId) {
                return ApiResponse.fail("COMMUNITY_SERVICE_UNAVAILABLE", "社区服务不可用，请稍后重试");
            }
        };
    }
}
