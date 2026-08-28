package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 相遇信笺（Sprint 3.3，文档 2.10 契约）：匿名化——不含对方
 * userId/昵称/头像；status=PENDING 时 content 返回 null（契约）。
 */
@Schema(description = "相遇信笺（匿名）")
public record EncounterLetterVO(
        Long letterId,
        List<String> wishTags,
        LocalDateTime encounterTime,
        String encounterGeohash6,
        String status,
        /** PENDING 时为 null；DELIVERED/READ 为诗意文案 */
        String content,
        LocalDateTime deliveredAt
) {
}
