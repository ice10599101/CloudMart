package com.cloudmart.software.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.dto.software.SoftwareReleaseNotifyRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 通知服务内部端点（方案 9.4，P1）：出站令牌 scope=notification:internal；
 * 批量按订阅者创建"软件更新"通知，通知服务按 eventId+userId 去重。
 */
@FeignClient(name = "mall-notification", contextId = "softwareNotificationFeignClient",
        fallbackFactory = SoftwareNotificationFeignClientFallbackFactory.class)
public interface SoftwareNotificationFeignClient {

    @PostMapping("/internal/notifications/software-release")
    ApiResponse<Integer> releasePublished(@RequestBody SoftwareReleaseNotifyRequest request);
}
