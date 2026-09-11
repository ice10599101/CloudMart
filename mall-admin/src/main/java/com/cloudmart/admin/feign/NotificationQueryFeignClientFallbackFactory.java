package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 通知记录查询 Feign 降级工厂（Sprint 2.4）。
 *
 * <p>降级语义与 NotificationFeignClientFallbackFactory 保持一致：
 * 记录完整堆栈后抛出降级业务异常（查询为只读操作，无 4xx 透传需求）。</p>
 */
@Component
@Slf4j
public class NotificationQueryFeignClientFallbackFactory implements FallbackFactory<NotificationQueryFeignClient> {

    @Override
    public NotificationQueryFeignClient create(Throwable cause) {
        log.error("通知记录查询服务调用失败", cause);
        return (userId, type, page, pageSize) -> {
            throw new BusinessException("NOTIFICATION_SERVICE_UNAVAILABLE", "通知服务不可用，请稍后重试");
        };
    }
}
