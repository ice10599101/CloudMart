package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * mall-notification 通知记录 Feign 客户端（Sprint 2.4 管理后台：
 * 通知推送记录查看）。
 *
 * <p>下游 /admin/notifications 由 @PreAuthorize("hasRole('INTERNAL')") 保护。</p>
 */
@FeignClient(contextId = "notificationQueryFeignClient", name = "mall-notification",
        path = "/admin/notifications", fallbackFactory = NotificationQueryFeignClientFallbackFactory.class)
public interface NotificationQueryFeignClient {

    @GetMapping
    ApiResponse<List<Map<String, Object>>> listNotifications(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize);
}
