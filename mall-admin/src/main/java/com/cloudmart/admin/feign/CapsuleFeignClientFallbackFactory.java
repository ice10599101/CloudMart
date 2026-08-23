package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 时间胶囊管理端 Feign 降级工厂（Sprint 2.4）。
 */
@Component
@Slf4j
public class CapsuleFeignClientFallbackFactory implements FallbackFactory<CapsuleFeignClient> {

    @Override
    public CapsuleFeignClient create(Throwable cause) {
        log.error("时间胶囊管理服务调用失败: {}", cause.getMessage());
        return () -> {
            throw new BusinessException("CAPSULE_SERVICE_UNAVAILABLE", "时间胶囊服务不可用，请稍后重试");
        };
    }
}
