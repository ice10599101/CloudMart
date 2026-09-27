package com.cloudmart.user.feign;

import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * mall-auth 认证状态客户端降级：失效失败必须显式失败——禁用/改密未同步撤销
 * 令牌时继续放行会留下未失效的登录态（fail-closed）。
 */
@Slf4j
@Component
public class AuthStateFeignClientFallbackFactory implements FallbackFactory<AuthStateFeignClient> {

    @Override
    public AuthStateFeignClient create(Throwable cause) {
        log.error("mall-auth 认证状态失效调用失败: {}", cause.getMessage());
        return request -> {
            throw new BusinessException("SESSION_UNAVAILABLE", "认证状态失效服务暂不可用，请稍后重试");
        };
    }
}
