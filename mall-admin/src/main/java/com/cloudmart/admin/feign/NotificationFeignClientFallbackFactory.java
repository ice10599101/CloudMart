package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.NotificationSearchRequest;
import com.cloudmart.admin.dto.feign.SendNotificationRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationFeignClientFallbackFactory implements FallbackFactory<NotificationFeignClient> {

    @Override
    public NotificationFeignClient create(Throwable cause) {
        log.error("通知服务调用失败: {}", cause.getMessage());
        return new NotificationFeignClient() {
            @Override
            public ApiResponse<Object> listNotifications(NotificationSearchRequest request) {
                throw new BusinessException("NOTIFICATION_SERVICE_UNAVAILABLE", "通知服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> sendNotification(SendNotificationRequest request) {
                throw new BusinessException("NOTIFICATION_SERVICE_UNAVAILABLE", "通知服务不可用，请稍后重试");
            }
        };
    }
}
