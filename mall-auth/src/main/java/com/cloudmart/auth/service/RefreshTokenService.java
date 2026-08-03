package com.cloudmart.auth.service;

import com.cloudmart.common.constant.SecurityConstants;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long refreshTokenExpiration;

    public RefreshTokenService(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               @Value("${auth.jwt.refresh-token-expiration:604800}") long refreshTokenExpiration) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String createRefreshToken(Long userId) {
        String tokenId = UUID.randomUUID().toString();
        String key = SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId;
        try {
            String value = objectMapper.writeValueAsString(Map.of(
                    "userId", userId.toString(),
                    "used", "false"
            ));
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(refreshTokenExpiration));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create refresh token", e);
        }

        String userKey = SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + userId;
        redisTemplate.opsForSet().add(userKey, tokenId);
        Duration ttl = Duration.ofSeconds(refreshTokenExpiration);
        Long currentTtl = redisTemplate.getExpire(userKey);
        if (currentTtl == null || currentTtl < ttl.getSeconds()) {
            redisTemplate.expire(userKey, ttl);
        }

        return tokenId;
    }

    public Long rotateRefreshToken(String tokenId) {
        String key = SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tokenId;
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> data = objectMapper.readValue(value, Map.class);
            Long userId = Long.valueOf(data.get("userId"));
            boolean used = "true".equals(data.get("used"));

            if (used) {
                revokeAllTokensForUser(userId);
                throw new IllegalStateException("Refresh token reuse detected for user: " + userId);
            }

            data.put("used", "true");
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data),
                    Duration.ofSeconds(refreshTokenExpiration));

            return userId;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to rotate refresh token", e);
        }
    }

    public void revokeAllTokensForUser(Long userId) {
        String userKey = SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + userId;
        Set<String> tokenIds = redisTemplate.opsForSet().members(userKey);
        if (tokenIds != null) {
            for (String tid : tokenIds) {
                redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tid);
            }
        }
        redisTemplate.delete(userKey);
    }
}
