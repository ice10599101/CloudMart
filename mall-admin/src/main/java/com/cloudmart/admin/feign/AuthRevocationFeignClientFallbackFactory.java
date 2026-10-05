package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * mall-auth 撤销客户端降级：撤销失败必须显式失败——静默吞掉会掩盖
 * "被踢用户令牌仍在流通" 的事实（fail-closed）。
 *
 * <p>诊断增强：{@link FeignException}（mall-auth 返回 4xx/5xx、连接失败等）
 * 将状态码与响应体摘要透传到 BusinessException message——网关响应即可读出
 * 真实原因（如 SEC01 REJECT 的 401/403），不再被统一文案掩盖。</p>
 */
@Slf4j
@Component
public class AuthRevocationFeignClientFallbackFactory implements FallbackFactory<AuthRevocationFeignClient> {

    @Override
    public AuthRevocationFeignClient create(Throwable cause) {
        log.error("mall-auth 令牌撤销调用失败: {}", cause.getMessage(), cause);
        String detail = describe(cause);
        return new AuthRevocationFeignClient() {
            @Override
            public ApiResponse<Void> revokeSubject(java.util.Map<String, String> request) {
                throw FeignBusinessErrors.parse(cause, "TOKEN_REVOCATION_UNAVAILABLE", "令牌撤销失败：" + detail);
            }

            @Override
            public ApiResponse<Void> invalidateState(java.util.Map<String, Object> request) {
                throw FeignBusinessErrors.parse(cause, "TOKEN_REVOCATION_UNAVAILABLE", "令牌撤销失败：" + detail);
            }
        };
    }

    private String describe(Throwable cause) {
        if (cause instanceof FeignException fe) {
            String body = fe.contentUTF8();
            return "HTTP " + fe.status()
                    + (body == null || body.isBlank() ? "" : " body=" + body);
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
