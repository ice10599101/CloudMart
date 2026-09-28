package com.cloudmart.auth.service;

import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SEC-01/03 会话账本服务：签发写入会话版本、失效递增主体当前版本（网关与
 * 直连服务据此拒绝旧令牌）、Redis 故障 fail-closed 不发令牌。
 */
@DisplayName("AuthSessionService 会话与认证状态版本")
class AuthSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    private AuthSessionService service;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        refreshTokenService = mock(RefreshTokenService.class);
        service = new AuthSessionService(redisTemplate, refreshTokenService,
                900L, 604800L, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("签发会话：记录当前主体版本，TTL 为访问令牌有效期")
    void issueSession_storesCurrentVersionWithAccessTokenTtl() {
        when(valueOperations.get("auth:auth_version:user:42")).thenReturn("5");

        AuthSessionService.IssuedSession issued = service.issueSession(SubjectType.USER, 42L);

        assertThat(issued.authVersion()).isEqualTo(5L);
        assertThat(issued.sid()).isNotBlank();
        verify(valueOperations).set(eq("auth:session_valid:" + issued.sid()), eq("5"),
                eq(Duration.ofSeconds(900)));
    }

    @Test
    @DisplayName("签发会话：Redis 写失败 fail-closed（SESSION_UNAVAILABLE），不发令牌")
    void issueSession_redisFailure_failsClosed() {
        when(valueOperations.get(anyString())).thenReturn("0");
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(900)));

        assertThatThrownBy(() -> service.issueSession(SubjectType.USER, 42L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SESSION_UNAVAILABLE");
    }

    @Test
    @DisplayName("当前版本：无记录视为 0，有记录返回实际值")
    void currentAuthVersion_defaultsToZero() {
        when(valueOperations.get("auth:auth_version:user:42")).thenReturn(null);
        assertThat(service.currentAuthVersion(SubjectType.USER, 42L)).isZero();

        when(valueOperations.get("auth:auth_version:admin:7")).thenReturn("2");
        assertThat(service.currentAuthVersion(SubjectType.ADMIN, 7L)).isEqualTo(2L);
    }

    @Test
    @DisplayName("当前版本查询失败 fail-closed：按 0 处理会让高版本凭据复活")
    void currentAuthVersion_redisFailure_failsClosed() {
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> service.currentAuthVersion(SubjectType.USER, 42L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SESSION_UNAVAILABLE");
    }

    @Test
    @DisplayName("软失效：递增主体版本并刷新 TTL，不动刷新令牌家族")
    void invalidate_soft_incrementsVersion() {
        when(valueOperations.increment("auth:auth_version:user:42")).thenReturn(1L);

        service.invalidate(SubjectType.USER, 42L, false);

        verify(redisTemplate).expire("auth:auth_version:user:42", Duration.ofSeconds(604800));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("硬失效：递增版本并撤销该主体全部刷新令牌家族")
    void invalidate_hard_revokesRefreshFamilies() {
        when(valueOperations.increment("auth:auth_version:admin:7")).thenReturn(3L);

        service.invalidate(SubjectType.ADMIN, 7L, true);

        verify(redisTemplate).expire("auth:auth_version:admin:7", Duration.ofSeconds(604800));
        verify(refreshTokenService).revokeAllTokensForSubject(SubjectType.ADMIN, 7L);
    }

    @Test
    @DisplayName("失效时 Redis 故障 fail-closed，不撤销刷新令牌（避免半程失效）")
    void invalidate_redisFailure_failsClosed() {
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> service.invalidate(SubjectType.USER, 42L, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SESSION_UNAVAILABLE");
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("撤销会话：删除会话键；sid 为空时无操作")
    void revokeSession_deletesKey() {
        service.revokeSession("session-1");
        verify(redisTemplate).delete("auth:session_valid:session-1");

        service.revokeSession(" ");
        verify(redisTemplate).delete("auth:session_valid:session-1");
    }
}
