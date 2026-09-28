package com.cloudmart.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 会话/认证状态版本校验器（SEC-03）。
 *
 * <p>权威记录：{@code auth:session_valid:{sid}} → 签发时的认证状态版本。
 * 令牌携带的 authVersion 必须与会话记录一致；会话被删除（登出/踢出）或版本
 * 不匹配（禁用/改密/权限变更）都判定失效。</p>
 *
 * <p>响应式查询不阻塞网关事件循环；Redis 故障时 {@code error} 信号由调用方
 * fail-closed 处理（拒绝认证），绝不放行未校验会话。</p>
 */
@Slf4j
@Component
public class SessionValidator {

    private final ReactiveStringRedisTemplate reactiveRedisTemplate;

    private final String keyPrefix;

    public SessionValidator(ReactiveStringRedisTemplate reactiveRedisTemplate,
                            @Value("${gateway.session.key-prefix:auth:session_valid:}") String keyPrefix) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.keyPrefix = keyPrefix;
    }

    /**
     * @return Mono.just(true) 会话有效；Mono.just(false) 会话缺失或版本不匹配；
     *         Mono.error Redis 故障（调用方 fail-closed）
     */
    public Mono<Boolean> isSessionValid(String sid, String expectedVersion) {
        return reactiveRedisTemplate.opsForValue().get(keyPrefix + sid)
                .map(actual -> actual != null && actual.equals(expectedVersion))
                .defaultIfEmpty(false)
                .doOnError(e -> log.error("[SEC03] 会话校验 Redis 访问失败（fail-closed）: {}", e.getMessage()));
    }
}
