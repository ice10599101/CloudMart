package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 心愿删除结果 VO（对应文档 2.1 DELETE /wish/wishes/{id} 软删响应）。
 */
@Schema(name = "WishDeleteResultVO", description = "心愿删除结果")
public record WishDeleteResultVO(
        @Schema(description = "心愿 ID") Long id,
        @Schema(description = "软删除时间") LocalDateTime deletedAt
) {}
