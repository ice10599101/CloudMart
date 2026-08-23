package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 通知记录查询 Feign 降级工厂（Sprint 2.4）。
 */
@Component
@Slf4j
public class NotificationQueryFeignClientFallbackFactory implements FallbackFactory<NotificationQueryFeignClient> {

    @Override
    public NotificationQueryFeignClient create(Throwable cause) {
        log.error("通知记录查询服务调用失败: {}", cause.getMessage());
        return (userId, type, page, pageSize) -> {
            throw new BusinessException("NOTIFICATION_SERVICE_UNAVAILABLE", "通知服务不可用，请稍后重试");
        };
    }
}
