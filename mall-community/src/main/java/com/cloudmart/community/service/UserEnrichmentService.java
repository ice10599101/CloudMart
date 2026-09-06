package com.cloudmart.community.service;

import com.cloudmart.community.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEnrichmentService {

    private final UserFeignClient userFeignClient;

    private static final long CACHE_TTL_MS = 60_000;
    private final Map<Long, CachedUser> userCache = new ConcurrentHashMap<>();

    public Map<Long, UserInfo> batchGetUsers(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, UserInfo> result = new HashMap<>();
        Set<Long> missingIds = new HashSet<>();

        long now = System.currentTimeMillis();
        for (Long id : userIds) {
            CachedUser cached = userCache.get(id);
            if (cached != null && (now - cached.cachedAt) < CACHE_TTL_MS) {
                result.put(id, cached.info);
            } else {
                missingIds.add(id);
            }
        }

        if (!missingIds.isEmpty()) {
            try {
                var response = userFeignClient.batchGetUsers(new ArrayList<>(missingIds));
                if (response != null && response.data() != null) {
                    for (var userMap : response.data()) {
                        UserInfo info = extractUserInfo(userMap);
                        result.put(info.id, info);
                        userCache.put(info.id, new CachedUser(info, now));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch users from user service: {}", e.getMessage());
            }
        }

        for (Long id : userIds) {
            result.putIfAbsent(id, new UserInfo(id, "用户" + id, null, null, null));
        }

        return result;
    }

    public UserInfo getSingleUser(Long userId) {
        if (userId == null) {
            return new UserInfo(0L, "未知用户", null, null, null);
        }
        Map<Long, UserInfo> users = batchGetUsers(Set.of(userId));
        return users.getOrDefault(userId, new UserInfo(userId, "用户" + userId, null, null, null));
    }

    public Map<Long, UserInfo> searchUsersByNickname(String nickname) {
        try {
            var response = userFeignClient.searchUsers(nickname, 1, 5);
            if (response != null && response.data() != null) {
                Map<Long, UserInfo> result = new HashMap<>();
                for (var userMap : response.data()) {
                    UserInfo info = extractUserInfo(userMap);
                    if (info.nickname().equals(nickname)) {
                        result.put(info.id, info);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to search users by nickname '{}': {}", nickname, e.getMessage());
        }
        return Map.of();
    }

    /**
     * 关键词搜索用户（宽松匹配：昵称或小答号包含即命中），
     * 供私信发起会话等场景选择目标用户。
     */
    public List<Map<String, Object>> searchUsersByKeyword(String keyword, int page, int pageSize) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            var response = userFeignClient.searchUsers(keyword.trim(), page, pageSize);
            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.warn("Failed to search users by keyword '{}': {}", keyword, e.getMessage());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private UserInfo extractUserInfo(Object userMap) {
        if (userMap instanceof Map map) {
            Long id = map.get("id") instanceof Number n ? n.longValue() : 0L;
            String nickname = map.get("nickname") instanceof String s ? s : "用户" + id;
            String avatar = map.get("avatar") instanceof String s ? s : null;
            String username = map.get("username") instanceof String s ? s : null;
            String signature = map.get("signature") instanceof String s ? s : null;
            return new UserInfo(id, nickname, avatar, username, signature);
        }
        return new UserInfo(0L, "未知用户", null, null, null);
    }

    public record UserInfo(Long id, String nickname, String avatar, String username, String signature) {}

    private record CachedUser(UserInfo info, long cachedAt) {}
}
