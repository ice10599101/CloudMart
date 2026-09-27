package com.cloudmart.admin.service.impl;

import com.cloudmart.admin.dto.AdminOnlineUserResponse;
import com.cloudmart.admin.feign.AuthRevocationFeignClient;
import com.cloudmart.common.constant.SecurityConstants;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOnlineUserServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private AuthRevocationFeignClient authRevocationFeignClient;
    private AdminOnlineUserServiceImpl adminOnlineUserService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        authRevocationFeignClient = mock(AuthRevocationFeignClient.class);
        adminOnlineUserService = new AdminOnlineUserServiceImpl(redisTemplate, objectMapper, authRevocationFeignClient);
    }

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("empty keys -> returns empty list")
        void list_EmptyKeys_ShouldReturnEmptyList() {
            when(redisTemplate.keys(SecurityConstants.ADMIN_ONLINE_PREFIX + "*")).thenReturn(Set.of());

            List<AdminOnlineUserResponse> result = adminOnlineUserService.list();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("with online users -> returns parsed responses")
        @SuppressWarnings("unchecked")
        void list_WithOnlineUsers_ShouldReturnResponses() throws Exception {
            String key = SecurityConstants.ADMIN_ONLINE_PREFIX + "token-abc";
            when(redisTemplate.keys(SecurityConstants.ADMIN_ONLINE_PREFIX + "*")).thenReturn(Set.of(key));

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            String json = """
                {"userId":"1","username":"admin","ipaddr":"127.0.0.1","loginTime":"2025-01-01T10:00:00"}\
                """;
            when(valueOps.get(key)).thenReturn(json);

            Map<String, String> infoMap = Map.of(
                    "userId", "1",
                    "username", "admin",
                    "ipaddr", "127.0.0.1",
                    "loginTime", "2025-01-01T10:00:00"
            );
            when(objectMapper.readValue(eq(json), any(TypeReference.class))).thenReturn(infoMap);

            List<AdminOnlineUserResponse> result = adminOnlineUserService.list();

            assertThat(result).hasSize(1);
            AdminOnlineUserResponse response = result.getFirst();
            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.username()).isEqualTo("admin");
            assertThat(response.loginIp()).isEqualTo("127.0.0.1");
            assertThat(response.tokenId()).isEqualTo("token-abc");
        }

        @Test
        @DisplayName("exception during Redis access -> returns empty list")
        void list_Exception_ShouldReturnEmptyList() {
            when(redisTemplate.keys(SecurityConstants.ADMIN_ONLINE_PREFIX + "*"))
                    .thenThrow(new RuntimeException("Redis connection failed"));

            List<AdminOnlineUserResponse> result = adminOnlineUserService.list();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("forceLogout")
    class ForceLogoutTests {

        @Test
        @DisplayName("existing token -> deletes key and associated refresh tokens")
        @SuppressWarnings("unchecked")
        void forceLogout_ExistingToken_ShouldDeleteKey() throws Exception {
            String tokenId = "token-abc";
            String key = SecurityConstants.ADMIN_ONLINE_PREFIX + tokenId;
            String json = """
                {"userId":"42","username":"admin"}\
                """;

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn(json);

            Map<String, String> infoMap = Map.of("userId", "42", "username", "admin");
            when(objectMapper.readValue(eq(json), any(TypeReference.class))).thenReturn(infoMap);

            adminOnlineUserService.forceLogout(tokenId);

            // SEC-02：刷新令牌家族撤销经 mall-auth 内部接口执行
            verify(authRevocationFeignClient).revokeSubject(AuthRevocationFeignClient.adminSubject(42L));
            verify(redisTemplate).delete(key);
        }

        @Test
        @DisplayName("non-existing token -> does nothing")
        void forceLogout_NonExistingToken_ShouldNoop() {
            String tokenId = "nonexistent";
            String key = SecurityConstants.ADMIN_ONLINE_PREFIX + tokenId;

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(key)).thenReturn(null);

            adminOnlineUserService.forceLogout(tokenId);

            verify(redisTemplate, never()).delete(anyString());
        }
    }
}
