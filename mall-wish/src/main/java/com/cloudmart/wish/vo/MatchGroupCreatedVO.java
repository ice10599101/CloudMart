package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 建组结果（Sprint 2.6，文档 2.8 契约）。
 */
@Schema(description = "建组结果")
public record MatchGroupCreatedVO(
        Long groupId,
        String keyword,
        Integer maxMembers,
        String status,
        String role,
        LocalDateTime joinedAt
) {
}
