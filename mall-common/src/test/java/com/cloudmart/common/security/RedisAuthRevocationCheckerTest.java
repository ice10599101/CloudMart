package com.cloudmart.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SEC-01 直连服务撤销状态检查：会话账本 + 主体认证状态版本双记录校验，
 * Redis 故障 fail-closed（抛 AuthStateException，由过滤器拒绝）。
 */
@DisplayName("RedisAuthRevocationChecker 撤销状态检查")
class RedisAuthRevocationCheckerTest {

    private static final String SESSION_KEY = "auth:session_valid:session-1";
    private static final String VERSION_KEY = "auth:auth_version:user:42";

    private RedisAuthRevocationChecker checker;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        checker = new RedisAuthRevocationChecker(redisTemplate);
    }

    private void stubSession(String sessionVersion) {
        when(valueOperations.get(SESSION_KEY)).thenReturn(sessionVersion);
    }

    private void stubSubjectVersion(String currentVersion) {
        when(valueOperations.get(VERSION_KEY)).thenReturn(currentVersion);
    }

    @Test
    @DisplayName("会话存在且版本一致、主体无版本记录（视为 0）→ 有效")
    void active_sessionMatches_subjectVersionDefaultsToZero() {
        stubSession("0");

        assertThat(checker.isActive("user", "42", "session-1", 0L)).isTrue();
    }

    @Test
    @DisplayName("会话存在且版本一致、主体当前版本一致 → 有效")
    void active_sessionAndSubjectVersionMatch() {
        stubSession("3");
        stubSubjectVersion("3");

        assertThat(checker.isActive("user", "42", "session-1", 3L)).isTrue();
    }

    @Test
    @DisplayName("会话记录缺失（登出/踢出）→ 已撤销")
    void revoked_sessionMissing() {
        when(valueOperations.get(SESSION_KEY)).thenReturn(null);

        assertThat(checker.isActive("user", "42", "session-1", 0L)).isFalse();
    }

    @Test
    @DisplayName("会话版本与令牌声明不一致 → 已撤销")
    void revoked_sessionVersionMismatch() {
        stubSession("1");

        assertThat(checker.isActive("user", "42", "session-1", 0L)).isFalse();
    }

    @Test
    @DisplayName("主体版本已递增（禁用/改密/权限变更）→ 旧令牌已撤销")
    void revoked_subjectVersionIncremented() {
        stubSession("0");
        stubSubjectVersion("1");

        assertThat(checker.isActive("user", "42", "session-1", 0L)).isFalse();
    }

    @Test
    @DisplayName("主体版本记录缺失但令牌版本非 0 → 拒绝")
    void revoked_subjectVersionMissingButTokenNonZero() {
        stubSession("2");

        assertThat(checker.isActive("user", "42", "session-1", 2L)).isFalse();
    }

    @Test
    @DisplayName("Redis 故障 → 抛 AuthStateException（fail-closed），不解释为有效")
    void redisFailure_failsClosed() {
        when(valueOperations.get(SESSION_KEY)).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> checker.isActive("user", "42", "session-1", 0L))
                .isInstanceOf(AuthStateException.class)
                .hasMessageContaining("fail-closed");
    }

    @Test
    @DisplayName("版本记录损坏（非数字）→ fail-closed，不猜测版本")
    void corruptedVersion_failsClosed() {
        stubSession("0");
        stubSubjectVersion("not-a-number");

        assertThatThrownBy(() -> checker.isActive("user", "42", "session-1", 0L))
                .isInstanceOf(AuthStateException.class);
    }
}
