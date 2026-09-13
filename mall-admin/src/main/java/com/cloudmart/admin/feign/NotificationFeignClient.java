package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.BroadcastNotificationRequest;
import com.cloudmart.admin.dto.feign.SendNotificationRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(contextId = "notificationFeignClient", name = "mall-notification", path = "/admin/notifications", fallbackFactory = NotificationFeignClientFallbackFactory.class)
public interface NotificationFeignClient {

    @PostMapping
    ApiResponse<Object> sendNotification(@RequestBody SendNotificationRequest request);

    /** 全站广播：由 mall-notification 枚举全量会员逐用户落库 + WS 推送（JSON body，防 XssFilter 转义与 URL 截断） */
    @PostMapping("/broadcast")
    ApiResponse<Object> broadcastNotification(@RequestBody BroadcastNotificationRequest request);

    /** 管理端删除指定通知（撤回误发内容） */
    @DeleteMapping("/{notificationId}")
    ApiResponse<Void> deleteNotification(@PathVariable("notificationId") Long notificationId);
}
