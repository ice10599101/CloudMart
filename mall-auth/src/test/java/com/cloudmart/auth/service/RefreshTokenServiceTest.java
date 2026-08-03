package com.cloudmart.auth.service;

import com.cloudmart.common.constant.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private RefreshTokenService refreshTokenService;

    private static final Long USER_ID = 42L;
    private static final long REFRESH_TOKEN_EXPIRATION = 604800L;

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperations;

    @SuppressWarnings("unchecked")
    private SetOperations<String, String> setOperations;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();
        refreshTokenService = new RefreshTokenService(redisTemplate, objectMapper, REFRESH_TOKEN_EXPIRATION);

        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Nested
    @DisplayName("createRefreshToken")
    class CreateRefreshTokenTests {

        @Test
        @DisplayName("should create token and store in Redis with correct TTL")
        void createRefreshToken_storesInRedis() {
            when(redisTemplate.getExpire(anyString())).thenReturn(0L);

            String tokenId = refreshTokenService.createRefreshToken(USER_ID);

            assertThat(tokenId).isNotBlank();
            verify(valueOperations).set(
                    eq(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId),
                    anyString(),
                    eq(Duration.ofSeconds(REFRESH_TOKEN_EXPIRATION))
            );
        }

        @Test
        @DisplayName("should add token ID to user set in Redis")
        void createRefreshToken_addsToUserSet() {
            when(redisTemplate.getExpire(anyString())).thenReturn(0L);

            String tokenId = refreshTokenService.createRefreshToken(USER_ID);

            String userKey = SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + USER_ID;
            verify(redisTemplate.opsForSet()).add(eq(userKey), eq(tokenId));
        }

        @Test
        @DisplayName("should store userId and used=false in token value")
        void createRefreshToken_storesCorrectValue() throws Exception {
            when(redisTemplate.getExpire(anyString())).thenReturn(0L);

            String tokenId = refreshTokenService.createRefreshToken(USER_ID);

            String expectedKey = SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId;
            verify(valueOperations).set(eq(expectedKey), anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("rotateRefreshToken")
    class RotateRefreshTokenTests {

        @Test
        @DisplayName("should return userId when token is valid and unused")
        void rotateRefreshToken_validUnusedToken_returnsUserId() throws Exception {
            String tokenId = "test-token-id";
            String key = SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId;
            String value = objectMapper.writeValueAsString(Map.of("userId", USER_ID.toString(), "used", "false"));

            when(valueOperations.get(key)).thenReturn(value);

            Long result = refreshTokenService.rotateRefreshToken(tokenId);

            assertThat(result).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("should mark token as used after rotation")
        void rotateRefreshToken_marksTokenAsUsed() throws Exception {
            String tokenId = "test-token-id";
            String key = SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId;
            String value = objectMapper.writeValueAsString(Map.of("userId", USER_ID.toString(), "used", "false"));

            when(valueOperations.get(key)).thenReturn(value);

            refreshTokenService.rotateRefreshToken(tokenId);

            verify(valueOperations).set(eq(key), anyString(), eq(Duration.ofSeconds(REFRESH_TOKEN_EXPIRATION)));
        }

        @Test
        @DisplayName("should return null when token does not exist")
        void rotateRefreshToken_nonExistentToken_returnsNull() {
            String tokenId = "non-existent-token";
            String key = SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId;

            when(valueOperations.get(key)).thenReturn(null);

            Long result = refreshTokenService.rotateRefreshToken(tokenId);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should revoke all tokens and throw when token reuse is detected")
        void rotateRefreshToken_reusedToken_revokesAllAndThrows() throws Exception {
            String tokenId = "reused-token-id";
            String key = SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId;
            String value = objectMapper.writeValueAsString(Map.of("userId", USER_ID.toString(), "used", "true"));

            when(valueOperations.get(key)).thenReturn(value);
            String userKey = SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + USER_ID;
            when(redisTemplate.opsForSet().members(userKey)).thenReturn(Set.of(tokenId));

            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(tokenId))
                    .isInstanceOf(IllegalStateException.class);

            verify(redisTemplate).delete(key);
            verify(redisTemplate).delete(userKey);
        }
    }

    @Nested
    @DisplayName("revokeAllTokensForUser")
    class RevokeAllTokensForUserTests {

        @Test
        @DisplayName("should delete all token keys and user set")
        void revokeAllTokensForUser_deletesAllTokens() {
            String tokenId1 = "token-1";
            String tokenId2 = "token-2";
            String userKey = SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + USER_ID;

            when(redisTemplate.opsForSet().members(userKey)).thenReturn(Set.of(tokenId1, tokenId2));

            refreshTokenService.revokeAllTokensForUser(USER_ID);

            verify(redisTemplate).delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId1);
            verify(redisTemplate).delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId2);
            verify(redisTemplate).delete(userKey);
        }

        @Test
        @DisplayName("should handle null token set gracefully")
        void revokeAllTokensForUser_nullTokenSet_doesNotThrow() {
            String userKey = SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + USER_ID;

            when(setOperations.members(userKey)).thenReturn(null);

            refreshTokenService.revokeAllTokensForUser(USER_ID);

            verify(redisTemplate).delete(userKey);
        }
    }
}
