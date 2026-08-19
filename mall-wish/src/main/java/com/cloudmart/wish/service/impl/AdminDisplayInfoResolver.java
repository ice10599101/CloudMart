package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台展示信息解析器（Sprint 1.2）。
 *
 * <p>互动/评论管理列表共用的批量关联信息填充：
 * 心愿标题（含软删心愿，审计需保留标题）与用户昵称（Feign，降级占位）。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminDisplayInfoResolver {

    private static final String NICKNAME_PLACEHOLDER = "心愿旅人";

    private final WishMapper wishMapper;
    private final UserFeignClient userFeignClient;

    /**
     * 批量获取心愿标题（含已软删心愿）。
     *
     * @param wishIds 心愿 ID 集合
     * @return wishId → 标题（标题为空时映射为空串）
     */
    public Map<Long, String> fetchWishTitles(Set<Long> wishIds) {
        if (wishIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return wishMapper.selectBatchIdsIncludingDeleted(wishIds).stream()
                .collect(Collectors.toMap(Wish::getId, w -> w.getTitle() == null ? "" : w.getTitle()));
    }

    /**
     * 批量获取用户昵称（Feign，降级返回空 Map，展示层用占位昵称）。
     *
     * @param userIds 用户 ID 集合
     * @return userId → 昵称
     */
    public Map<Long, String> fetchUserNicknames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response.success() && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> (String) m.getOrDefault("nickname", NICKNAME_PLACEHOLDER)));
            }
        } catch (Exception e) {
            log.warn("管理后台批量获取用户昵称失败，降级为占位昵称: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
