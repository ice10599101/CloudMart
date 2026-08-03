package com.cloudmart.admin.service.impl;

import com.cloudmart.admin.dto.AdminOnlineUserResponse;
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

    public AdminOnlineUserServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AdminOnlineUserResponse> list() {
        try {
            var keys = redisTemplate.keys(SecurityConstants.ADMIN_ONLINE_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return Collections.emptyList();
            }

            List<AdminOnlineUserResponse> result = new ArrayList<>();
            for (String key : keys) {
                String value = redisTemplate.opsForValue().get(key);
                if (value == null) {
                    continue;
                }
                Map<String, String> info = objectMapper.readValue(value, new TypeReference<>() {});
                String tokenId = key.substring(SecurityConstants.ADMIN_ONLINE_PREFIX.length());
                result.add(new AdminOnlineUserResponse(
                        Long.valueOf(info.get("userId")),
                        info.get("username"),
                        info.get("username"),
                        null,
                        info.get("ipaddr"),
                        parseDateTime(info.get("loginTime")),
                        tokenId
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to list online users: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void forceLogout(String tokenId) {
        String key = SecurityConstants.ADMIN_ONLINE_PREFIX + tokenId;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            try {
                Map<String, String> info = objectMapper.readValue(value, new TypeReference<>() {});
                String userId = info.get("userId");
                if (userId != null) {
                    String userTokenKey = SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + userId;
                    var tokenIds = redisTemplate.opsForSet().members(userTokenKey);
                    if (tokenIds != null) {
                        for (String tid : tokenIds) {
                            redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + tid);
                        }
                    }
                    redisTemplate.delete(userTokenKey);
                }
            } catch (Exception e) {
                log.warn("Failed to revoke tokens for force logout: {}", e.getMessage());
            }
            redisTemplate.delete(key);
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
