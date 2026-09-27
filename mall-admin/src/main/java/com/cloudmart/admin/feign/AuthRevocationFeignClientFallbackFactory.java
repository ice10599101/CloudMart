package com.cloudmart.admin.feign;

import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * mall-auth 撤销客户端降级：撤销失败必须显式失败——静默吞掉会掩盖
 * "被踢用户令牌仍在流通" 的事实（fail-closed）。
 */
@Slf4j
@Component
public class AuthRevocationFeignClientFallbackFactory implements FallbackFactory<AuthRevocationFeignClient> {

    @Override
    public AuthRevocationFeignClient create(Throwable cause) {
        log.error("mall-auth 令牌撤销调用失败: {}", cause.getMessage());
        return request -> {
            throw new BusinessException("TOKEN_REVOCATION_UNAVAILABLE", "令牌撤销服务暂不可用，请稍后重试");
        };
    }
}
