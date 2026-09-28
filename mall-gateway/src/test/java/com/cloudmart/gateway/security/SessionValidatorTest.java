package com.cloudmart.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SEC-01 网关会话校验：绑定 subjectType/subjectId/sid，会话记录与主体当前
 * 版本都须与令牌 authVersion 一致；Redis 故障以 error 信号交调用方 fail-closed。
 */
@DisplayName("SessionValidator 会话与主体版本校验")
class SessionValidatorTest {

    private SessionValidator validator;
    private ReactiveValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        validator = new SessionValidator(redisTemplate, "auth:session_valid:", "auth:auth_version:");
    }

    private void stubSession(String sid, String version) {
        when(valueOperations.get("auth:session_valid:" + sid))
                .thenReturn(version == null ? Mono.empty() : Mono.just(version));
    }

    private void stubSubjectVersion(String subjectType, String subjectId, String version) {
        when(valueOperations.get("auth:auth_version:" + subjectType + ":" + subjectId))
                .thenReturn(version == null ? Mono.empty() : Mono.just(version));
    }

    @Test
    @DisplayName("会话存在、版本一致、主体无版本记录（视为 0）→ 有效")
    void valid_sessionMatches_subjectVersionDefaultsToZero() {
        stubSession("session-1", "0");
        stubSubjectVersion("user", "42", null);

        assertThat(validator.isSessionValid("user", "42", "session-1", "0").block()).isTrue();
    }

    @Test
    @DisplayName("会话与主体当前版本都与令牌一致 → 有效")
    void valid_sessionAndSubjectVersionMatch() {
        stubSession("session-1", "3");
        stubSubjectVersion("user", "42", "3");

        assertThat(validator.isSessionValid("user", "42", "session-1", "3").block()).isTrue();
    }

    @Test
    @DisplayName("会话缺失（登出/踢出）→ 无效")
    void invalid_sessionMissing() {
        stubSession("session-1", null);

        assertThat(validator.isSessionValid("user", "42", "session-1", "0").block()).isFalse();
    }

    @Test
    @DisplayName("会话版本与令牌不一致 → 无效")
    void invalid_sessionVersionMismatch() {
        stubSession("session-1", "1");

        assertThat(validator.isSessionValid("user", "42", "session-1", "0").block()).isFalse();
    }

    @Test
    @DisplayName("主体版本已递增（invalidate 后）→ 旧令牌无效——修复版本断链")
    void invalid_subjectVersionIncremented() {
        stubSession("session-1", "0");
        stubSubjectVersion("user", "42", "1");

        assertThat(validator.isSessionValid("user", "42", "session-1", "0").block()).isFalse();
    }

    @Test
    @DisplayName("主体版本记录缺失但令牌版本非 0 → 无效")
    void invalid_subjectVersionMissingButTokenNonZero() {
        stubSession("session-1", "2");
        stubSubjectVersion("user", "42", null);

        assertThat(validator.isSessionValid("user", "42", "session-1", "2").block()).isFalse();
    }

    @Test
    @DisplayName("管理员身份域使用各自的版本键（user/admin 不串）")
    void adminSubject_usesOwnVersionKey() {
        stubSession("session-a", "0");
        stubSubjectVersion("admin", "7", "0");

        assertThat(validator.isSessionValid("admin", "7", "session-a", "0").block()).isTrue();
    }

    @Test
    @DisplayName("Redis 故障 → error 信号（调用方 fail-closed），不返回有效")
    void redisFailure_errorSignal() {
        when(valueOperations.get("auth:session_valid:session-1"))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        assertThatThrownBy(() -> validator.isSessionValid("user", "42", "session-1", "0").block())
                .isInstanceOf(IllegalStateException.class);
    }
}
