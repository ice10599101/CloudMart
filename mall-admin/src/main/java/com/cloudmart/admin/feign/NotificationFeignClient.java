package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.NotificationSearchRequest;
import com.cloudmart.admin.dto.feign.SendNotificationRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

@FeignClient(contextId = "notificationFeignClient", name = "mall-notification", path = "/admin/notifications", fallbackFactory = NotificationFeignClientFallbackFactory.class)
public interface NotificationFeignClient {

    @GetMapping
    ApiResponse<Object> listNotifications(@SpringQueryMap NotificationSearchRequest request);

    @PostMapping
    ApiResponse<Object> sendNotification(@RequestBody SendNotificationRequest request);
}
