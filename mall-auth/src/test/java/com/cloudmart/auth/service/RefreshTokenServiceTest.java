package com.cloudmart.auth.service;

import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-02 刷新令牌服务测试：身份域隔离、轮换结果映射、重放撤销、
 * fail-closed 与撤销语义。
 */
class RefreshTokenServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ADMIN_ID = 7L;
    private static final long REFRESH_TOKEN_EXPIRATION = 604800L;
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        refreshTokenService = new RefreshTokenService(redisTemplate, REFRESH_TOKEN_EXPIRATION,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("创建")
    class CreateTests {

        @Test
        @DisplayName("用户域令牌带 u: 前缀且写入家族账本与主体索引")
        void createUserToken_prefixedAndPersisted() {
            String token = refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1");

            assertThat(token).startsWith("u:");
            verify(hashOperations).putAll(eq(RefreshTokenService.FAMILY_KEY_PREFIX + familyIdOf(token)),
                    any(java.util.Map.class));
            verify(setOperations).add(RefreshTokenService.SUBJECT_INDEX_PREFIX + "user:42",
                    familyIdOf(token));
        }

        @Test
        @DisplayName("管理员域令牌带 a: 前缀")
        void createAdminToken_prefixed() {
            String token = refreshTokenService.createRefreshToken(SubjectType.ADMIN, ADMIN_ID, "session-a");

            assertThat(token).startsWith("a:");
        }

        @Test
        @DisplayName("Redis 故障时 fail-closed：拒绝签发")
        void create_redisFailure_failClosed() {
            org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                    .when(hashOperations).putAll(anyString(), any(java.util.Map.class));

            assertThatThrownBy(() -> refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "REFRESH_TOKEN_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("轮换")
    class RotateTests {

        @Test
        @DisplayName("跨域提交（用户入口拿管理员令牌）拒绝且不触碰 Redis")
        void crossDomain_rejected_withoutConsuming() {
            String adminToken = refreshTokenService.createRefreshToken(SubjectType.ADMIN, ADMIN_ID, "session-a");

            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(SubjectType.USER, adminToken))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "TOKEN_DOMAIN_MISMATCH");

            verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
        }

        @Test
        @DisplayName("格式非法的令牌拒绝")
        void malformedToken_rejected() {
            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(SubjectType.USER, "not-a-token"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");
        }

        @Test
        @DisplayName("轮换成功返回同家族新令牌与主体/TTL")
        void rotateOk_returnsFamilyToken() {
            String token = refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1");
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(List.of("OK", "600", String.valueOf(USER_ID)));

            RefreshTokenService.RotationResult result =
                    refreshTokenService.rotateRefreshToken(SubjectType.USER, token);

            assertThat(result.subjectId()).isEqualTo(USER_ID);
            assertThat(result.subjectType()).isEqualTo(SubjectType.USER);
            assertThat(result.tokenValue()).startsWith("u:").isNotEqualTo(token);
            assertThat(result.tokenValue()).contains(familyIdOf(token));
            assertThat(result.ttlSeconds()).isEqualTo(600L);
        }

        @Test
        @DisplayName("重放（脚本 REUSED）抛 TOKEN_REUSE_DETECTED")
        void reuseDetected_throws() {
            String token = refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1");
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(List.of("REUSED"));

            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(SubjectType.USER, token))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "TOKEN_REUSE_DETECTED");
        }

        @Test
        @DisplayName("已撤销家族（脚本 REVOKED）拒绝")
        void revokedFamily_rejected() {
            String token = refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1");
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(List.of("REVOKED"));

            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(SubjectType.USER, token))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "TOKEN_REUSE_DETECTED");
        }

        @Test
        @DisplayName("家族过期（脚本 EXPIRED）抛过期错误码")
        void expiredFamily_rejected() {
            String token = refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1");
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(List.of("EXPIRED"));

            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(SubjectType.USER, token))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "REFRESH_TOKEN_EXPIRED");
        }

        @Test
        @DisplayName("Redis 故障时 fail-closed：不签发新令牌")
        void rotate_redisFailure_failClosed() {
            String token = refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1");
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenThrow(new IllegalStateException("redis down"));

            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(SubjectType.USER, token))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "REFRESH_TOKEN_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("撤销")
    class RevokeTests {

        @Test
        @DisplayName("按主体撤销：索引内全部家族标记 revoked 且索引删除")
        void revokeAll_marksFamiliesRevoked() {
            String token = refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1");
            when(setOperations.members(RefreshTokenService.SUBJECT_INDEX_PREFIX + "user:42"))
                    .thenReturn(java.util.Set.of(familyIdOf(token)));

            refreshTokenService.revokeAllTokensForSubject(SubjectType.USER, USER_ID);

            verify(hashOperations).put(RefreshTokenService.FAMILY_KEY_PREFIX + familyIdOf(token),
                    "revoked", "1");
            verify(redisTemplate).delete(RefreshTokenService.SUBJECT_INDEX_PREFIX + "user:42");
        }

        @Test
        @DisplayName("撤销失败显式报错（不静默）")
        void revokeFailure_explicitError() {
            when(setOperations.members(anyString())).thenThrow(new IllegalStateException("redis down"));

            assertThatThrownBy(() -> refreshTokenService.revokeAllTokensForSubject(SubjectType.USER, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "REFRESH_TOKEN_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("家族会话绑定（SEC-02 当前设备撤销的基础）")
    class BindSessionTests {

        @Test
        @DisplayName("创建家族时绑定 sid")
        void create_bindsSid() {
            String token = refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1");

            org.mockito.ArgumentCaptor<java.util.Map<String, String>> captor =
                    org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
            verify(hashOperations).putAll(eq(RefreshTokenService.FAMILY_KEY_PREFIX + familyIdOf(token)),
                    captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue())
                    .containsEntry("sid", "session-1");
        }

        @Test
        @DisplayName("轮换后重绑新 sid 并返回旧 sid 供撤销")
        void bindFamilySession_returnsPreviousSid() {
            when(hashOperations.get(anyString(), eq("sid"))).thenReturn("session-1");

            String previousSid = refreshTokenService.bindFamilySession("u:family-1:token-9", "session-2");

            assertThat(previousSid).isEqualTo("session-1");
            verify(hashOperations).put(RefreshTokenService.FAMILY_KEY_PREFIX + "family-1", "sid", "session-2");
        }

        @Test
        @DisplayName("家族未绑定过 sid 时返回 null")
        void bindFamilySession_noPreviousSid_returnsNull() {
            when(hashOperations.get(anyString(), eq("sid"))).thenReturn("");

            assertThat(refreshTokenService.bindFamilySession("u:family-1:token-9", "session-2")).isNull();
        }

        @Test
        @DisplayName("绑定失败不抛异常（轮换已提交，重试会误触发重放撤销）")
        void bindFamilySession_redisFailure_bestEffort() {
            when(hashOperations.get(anyString(), eq("sid"))).thenThrow(new IllegalStateException("redis down"));

            assertThat(refreshTokenService.bindFamilySession("u:family-1:token-9", "session-2")).isNull();
        }
    }

    @Nested
    @DisplayName("按会话撤销家族（SEC-02 退出当前设备）")
    class RevokeBySessionTests {

        @Test
        @DisplayName("只撤销绑定当前 sid 的家族，其他设备家族保留")
        void revokeBySession_onlyMatchesBoundFamily() {
            when(setOperations.members(RefreshTokenService.SUBJECT_INDEX_PREFIX + "user:42"))
                    .thenReturn(java.util.Set.of("family-1", "family-2"));
            when(hashOperations.get(RefreshTokenService.FAMILY_KEY_PREFIX + "family-1", "sid"))
                    .thenReturn("session-1");
            when(hashOperations.get(RefreshTokenService.FAMILY_KEY_PREFIX + "family-2", "sid"))
                    .thenReturn("session-2");

            int revoked = refreshTokenService.revokeFamilyBySession(SubjectType.USER, USER_ID, "session-1");

            assertThat(revoked).isEqualTo(1);
            verify(hashOperations).put(RefreshTokenService.FAMILY_KEY_PREFIX + "family-1", "revoked", "1");
            verify(setOperations).remove(RefreshTokenService.SUBJECT_INDEX_PREFIX + "user:42", "family-1");
            verify(hashOperations, never()).put(RefreshTokenService.FAMILY_KEY_PREFIX + "family-2", "revoked", "1");
        }

        @Test
        @DisplayName("无匹配家族返回 0（幂等）")
        void revokeBySession_noMatch_returnsZero() {
            when(setOperations.members(anyString())).thenReturn(java.util.Set.of("family-1"));
            when(hashOperations.get(anyString(), eq("sid"))).thenReturn("session-other");

            assertThat(refreshTokenService.revokeFamilyBySession(SubjectType.USER, USER_ID, "session-1"))
                    .isZero();
        }

        @Test
        @DisplayName("sid 为空直接返回 0，不触碰 Redis")
        void revokeBySession_blankSid_noRedisAccess() {
            assertThat(refreshTokenService.revokeFamilyBySession(SubjectType.USER, USER_ID, " "))
                    .isZero();
            verify(redisTemplate, never()).opsForSet();
        }

        @Test
        @DisplayName("Redis 故障 fail-closed：撤销失败显式报错")
        void revokeBySession_redisFailure_failClosed() {
            when(setOperations.members(anyString())).thenThrow(new IllegalStateException("redis down"));

            assertThatThrownBy(() ->
                    refreshTokenService.revokeFamilyBySession(SubjectType.USER, USER_ID, "session-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "REFRESH_TOKEN_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("身份域标记")
    class SubjectTypeTests {

        @Test
        @DisplayName("u:/a: 前缀正确解析身份域；非法前缀返回 null")
        void parseDomainMarkers() {
            assertThat(SubjectType.fromToken("u:abc:def")).isEqualTo(SubjectType.USER);
            assertThat(SubjectType.fromToken("a:abc:def")).isEqualTo(SubjectType.ADMIN);
            assertThat(SubjectType.fromToken("x:abc:def")).isNull();
            assertThat(SubjectType.fromToken(null)).isNull();
        }
    }

    private static String familyIdOf(String tokenValue) {
        String[] parts = tokenValue.split(":", 3);
        return parts.length >= 2 ? parts[1] : "";
    }
}
