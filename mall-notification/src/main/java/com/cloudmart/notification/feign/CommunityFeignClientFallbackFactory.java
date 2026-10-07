package com.cloudmart.notification.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/** P2-24：社区服务不可用时 Fail-open 放行（策略见 CommunityFeignClient 注释） */
@Slf4j
@Component
public class CommunityFeignClientFallbackFactory implements FallbackFactory<CommunityFeignClient> {

    @Override
    public CommunityFeignClient create(Throwable cause) {
        return (userId, peerUserId) -> {
            log.warn("P2-24 拉黑状态查询失败（fail-open 放行）, userId={}, peerUserId={}, cause={}",
                    userId, peerUserId, cause.getMessage());
            return ApiResponse.ok(Map.of("blocked", false));
        };
    }
}
