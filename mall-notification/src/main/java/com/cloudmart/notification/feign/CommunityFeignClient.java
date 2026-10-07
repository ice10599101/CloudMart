package com.cloudmart.notification.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * P2-24：私信发送前的拉黑状态校验（mall-community 内部端点）。
 * Fail-open：社区服务不可用时放行并告警日志——私信可用性优先，
 * 拉黑过滤为体验类保护，不因跨服务抖动阻断核心通信。
 */
@FeignClient(name = "mall-community", contextId = "notificationCommunityFeignClient", fallbackFactory = CommunityFeignClientFallbackFactory.class)
public interface CommunityFeignClient {

    @GetMapping("/internal/blocks/status")
    ApiResponse<Map<String, Object>> blockStatus(@RequestParam("userId") Long userId,
                                                 @RequestParam("peerUserId") Long peerUserId);
}
