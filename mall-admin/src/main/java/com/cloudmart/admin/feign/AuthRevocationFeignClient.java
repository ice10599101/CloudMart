package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * mall-auth 内部撤销客户端（SEC-02）：踢人/禁用时撤销目标主体名下全部
 * 刷新令牌家族。令牌家族的键结构与轮换语义是 mall-auth 的私有实现，
 * 禁止跨服务直写其 Redis 键——撤销必须经此接口由 mall-auth 执行。
 *
 * <p>请求头由 ServiceTokenFeignInterceptor 自动签名
 * （iss=mall-admin，aud=mall-auth，scope=admin:auth）。</p>
 */
@FeignClient(contextId = "authRevocationFeignClient", name = "mall-auth",
        fallbackFactory = AuthRevocationFeignClientFallbackFactory.class)
public interface AuthRevocationFeignClient {

    @PostMapping("/internal/tokens/revoke-subject")
    ApiResponse<Void> revokeSubject(@RequestBody Map<String, String> request);

    /** 便捷构造：管理员域撤销请求体 */
    static Map<String, String> adminSubject(Long adminUserId) {
        return Map.of("subjectType", "ADMIN", "subjectId", String.valueOf(adminUserId));
    }

    @PostMapping("/invalidate-state")
    ApiResponse<Void> invalidateState(@RequestBody Map<String, Object> request);

    /** 便捷构造：认证状态硬失效（版本递增 + 撤销全部刷新令牌家族） */
    static Map<String, Object> adminHardInvalidate(Long adminUserId) {
        return Map.of("subjectType", "ADMIN", "subjectId", adminUserId, "revokeRefreshTokens", true);
    }

    /** 便捷构造：认证状态软失效（仅版本递增，用于角色/权限变更） */
    static Map<String, Object> adminSoftInvalidate(Long adminUserId) {
        return Map.of("subjectType", "ADMIN", "subjectId", adminUserId, "revokeRefreshTokens", false);
    }
}
