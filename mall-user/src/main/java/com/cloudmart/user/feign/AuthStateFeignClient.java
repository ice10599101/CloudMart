package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * mall-auth 认证状态客户端（SEC-03）：禁用用户/修改密码时使目标主体认证状态失效
 * （版本递增 + 撤销刷新令牌家族）。令牌撤销语义属于 mall-auth 私有实现，
 * 禁止跨服务直写其 Redis 键。
 *
 * <p>请求头由 ServiceTokenFeignInterceptor 自动签名
 * （iss=mall-user，aud=mall-auth，scope=admin:auth）。</p>
 */
@FeignClient(name = "mall-auth", contextId = "userAuthStateClient",
        fallbackFactory = AuthStateFeignClientFallbackFactory.class)
public interface AuthStateFeignClient {

    @PostMapping("/internal/tokens/invalidate-state")
    ApiResponse<Void> invalidateState(@RequestBody Map<String, Object> request);

    /** 便捷构造：硬失效请求体（版本递增 + 撤销全部刷新令牌） */
    static Map<String, Object> hardInvalidate(SubjectBody subject) {
        return Map.of("subjectType", subject.type(), "subjectId", subject.id(), "revokeRefreshTokens", true);
    }

    /** 主体标识（避免 feign 接口直接依赖枚举） */
    record SubjectBody(String type, Long id) {
    }
}
