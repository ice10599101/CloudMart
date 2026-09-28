package com.cloudmart.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 会话/认证状态版本校验器（SEC-01/03）。
 *
 * <p>两类权威记录（键格式与 mall-auth {@code AuthSessionService} 一致）：</p>
 * <ul>
 *   <li>会话记录 {@code auth:session_valid:{sid}} → 签发时的认证状态版本；
 *       删除（登出/踢出）即失效；</li>
 *   <li>主体当前版本 {@code auth:auth_version:{type}:{id}} → 禁用/改密/权限变更时
 *       递增。校验必须绑定 subjectType/subjectId/sid 并比较当前主体版本——仅比对
 *       会话记录无法感知 invalidate() 的版本递增，旧令牌会一直有效到自然过期。</li>
 * </ul>
 *
 * <p>令牌有效 ⇔ 会话存在 且 会话版本=令牌 authVersion 且 当前主体版本=令牌
 * authVersion。主体无版本记录视为 0（与签发时一致即有效）。</p>
 *
 * <p>响应式查询不阻塞网关事件循环；Redis 故障时 {@code error} 信号由调用方
 * fail-closed 处理（拒绝认证），绝不放行未校验会话。</p>
 */
@Slf4j
@Component
public class SessionValidator {

    private final ReactiveStringRedisTemplate reactiveRedisTemplate;

    private final String keyPrefix;

    private final String versionKeyPrefix;

    public SessionValidator(ReactiveStringRedisTemplate reactiveRedisTemplate,
                            @Value("${gateway.session.key-prefix:auth:session_valid:}") String keyPrefix) {
        this(reactiveRedisTemplate, keyPrefix, "auth:auth_version:");
    }

    SessionValidator(ReactiveStringRedisTemplate reactiveRedisTemplate,
                     String keyPrefix, String versionKeyPrefix) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.keyPrefix = keyPrefix;
        this.versionKeyPrefix = versionKeyPrefix;
    }

    /**
     * @param subjectType     令牌身份域（user/admin，已通过声明校验）
     * @param subjectId       令牌主体（sub）
     * @param sid             会话标识
     * @param expectedVersion 令牌携带的 authVersion
     * @return Mono.just(true) 会话有效；Mono.just(false) 会话缺失或任一版本不匹配；
     *         Mono.error Redis 故障（调用方 fail-closed）
     */
    public Mono<Boolean> isSessionValid(String subjectType, String subjectId,
                                        String sid, String expectedVersion) {
        return reactiveRedisTemplate.opsForValue().get(keyPrefix + sid)
                .flatMap(sessionVersion -> {
                    if (sessionVersion == null || !sessionVersion.equals(expectedVersion)) {
                        return Mono.just(false);
                    }
                    return reactiveRedisTemplate.opsForValue()
                            .get(versionKeyPrefix + subjectType + ":" + subjectId)
                            .map(current -> (current == null ? "0" : current).equals(expectedVersion))
                            .defaultIfEmpty("0".equals(expectedVersion));
                })
                .defaultIfEmpty(false)
                .doOnError(e -> log.error("[SEC01] 会话校验 Redis 访问失败（fail-closed）: {}", e.getMessage()));
    }
}
