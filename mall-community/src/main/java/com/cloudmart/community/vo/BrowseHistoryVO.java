package com.cloudmart.community.vo;

import java.time.LocalDateTime;

/** 浏览足迹展示对象。targetType+targetId 供前端跳转对应详情页。 */
public record BrowseHistoryVO(
        Long id,
        String targetType,
        Long targetId,
        String title,
        String cover,
        LocalDateTime viewedAt
) {}