package com.cloudmart.software.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareReleaseNotifyRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 通知服务降级工厂：订阅提醒失败如实抛错，由 fanout 投递行按指数退避重试
 * （人工重试保留同一通知去重键），绝不静默吞掉。
 */
@Slf4j
@Component
public class SoftwareNotificationFeignClientFallbackFactory
        implements FallbackFactory<SoftwareNotificationFeignClient> {

    @Override
    public SoftwareNotificationFeignClient create(Throwable cause) {
        log.warn("mall-notification 内部接口不可用: {}", cause.getMessage());
        return new SoftwareNotificationFeignClient() {
            @Override
            public ApiResponse<Integer> releasePublished(SoftwareReleaseNotifyRequest request) {
                throw new BusinessException("NOTIFICATION_SERVICE_UNAVAILABLE",
                        "通知服务暂不可用，请稍后重试", cause);
            }
        };
    }
}
