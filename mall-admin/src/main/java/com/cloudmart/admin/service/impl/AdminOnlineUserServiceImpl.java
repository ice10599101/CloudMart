package com.cloudmart.admin.service.impl;

import com.cloudmart.admin.dto.AdminOnlineUserResponse;
import com.cloudmart.admin.feign.AuthRevocationFeignClient;
import com.cloudmart.admin.service.AdminOnlineUserService;
import com.cloudmart.common.constant.SecurityConstants;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AdminOnlineUserServiceImpl implements AdminOnlineUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminOnlineUserServiceImpl.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthRevocationFeignClient authRevocationFeignClient;

    public AdminOnlineUserServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                      AuthRevocationFeignClient authRevocationFeignClient) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authRevocationFeignClient = authRevocationFeignClient;
    }

    @Override
    public List<AdminOnlineUserResponse> list() {
        try {
            List<AdminOnlineUserResponse> result = new ArrayList<>();
            appendOnlineUsers(SecurityConstants.USER_ONLINE_PREFIX, "USER", result);
            appendOnlineUsers(SecurityConstants.ADMIN_ONLINE_PREFIX, "ADMIN", result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to list online users: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void forceLogout(String tokenId) {
        boolean frontendUser = tokenId != null && tokenId.startsWith("USER:");
        String sessionId = frontendUser ? tokenId.substring("USER:".length()) : tokenId;
        String prefix = frontendUser ? SecurityConstants.USER_ONLINE_PREFIX : SecurityConstants.ADMIN_ONLINE_PREFIX;
        String key = prefix + sessionId;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            try {
                Map<String, String> info = objectMapper.readValue(value, new TypeReference<>() {});
                String userId = info.get("userId");
                if (userId != null) {
                    // SEC-02：刷新令牌家族的键结构是 mall-auth 私有实现，撤销必须经
                    // 其内部接口执行（admin:auth 服务令牌）；失败向上抛出，绝不静默。
                    if (frontendUser) {
                        authRevocationFeignClient.invalidateState(Map.of(
                                "subjectType", "USER",
                                "subjectId", Long.valueOf(userId),
                                "revokeRefreshTokens", true));
                    } else {
                        authRevocationFeignClient.revokeSubject(
                                AuthRevocationFeignClient.adminSubject(Long.valueOf(userId)));
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("强制下线时撤销刷新令牌失败: " + e.getMessage(), e);
            }
            if (frontendUser) {
                deleteOnlineEntriesForUser(SecurityConstants.USER_ONLINE_PREFIX, infoUserId(value), key);
            } else {
                redisTemplate.delete(key);
            }
        }
    }

    private void appendOnlineUsers(String prefix, String userType, List<AdminOnlineUserResponse> result)
            throws Exception {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                continue;
            }
            Map<String, String> info = objectMapper.readValue(value, new TypeReference<>() {});
            String sessionId = key.substring(prefix.length());
            String tokenId = "USER".equals(userType) ? "USER:" + sessionId : sessionId;
            result.add(new AdminOnlineUserResponse(
                    Long.valueOf(info.get("userId")),
                    info.get("username"),
                    info.getOrDefault("nickname", info.get("username")),
                    info.get("deptName"),
                    info.get("ipaddr"),
                    info.get("loginLocation"),
                    info.get("browser"),
                    info.get("os"),
                    parseDateTime(info.get("loginTime")),
                    tokenId,
                    userType));
        }
    }

    private String infoUserId(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, String>>() {}).get("userId");
        } catch (Exception e) {
            return null;
        }
    }

    private void deleteOnlineEntriesForUser(String prefix, String userId, String selectedKey) {
        redisTemplate.delete(selectedKey);
        if (userId == null) {
            return;
        }
        var keys = redisTemplate.keys(prefix + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);
            if (!selectedKey.equals(key) && value != null) {
                try {
                    Map<String, String> info = objectMapper.readValue(value, new TypeReference<>() {});
                    if (userId.equals(info.get("userId"))) {
                        redisTemplate.delete(key);
                    }
                } catch (Exception ignored) {
                    // Ignore malformed metadata and allow its short TTL to expire.
                }
            }
        }
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
