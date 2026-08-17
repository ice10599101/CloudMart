package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 心愿更新结果 VO（对应文档 2.1 PUT /wish/wishes/{id} 响应）。
 */
@Schema(name = "WishUpdateResultVO", description = "心愿更新结果")
public record WishUpdateResultVO(
        @Schema(description = "心愿 ID") Long id,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {}
