package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理后台互动列表 offset 分页查询参数（Sprint 1.2）。
 *
 * <p>支持按心愿/用户/类型/时间范围筛选，供互动趋势分析与审计场景使用。</p>
 */
@Schema(name = "AdminInteractionListQuery", description = "管理后台互动列表查询参数")
public record AdminInteractionListQuery(

        @Schema(description = "心愿 ID 筛选（可空）")
        Long wishId,

        @Schema(description = "互动用户 ID 筛选（可空）")
        Long userId,

        @Schema(description = "互动类型筛选（可空）")
        InteractionType type,

        @Schema(description = "互动时间起（可空，含）")
        LocalDateTime startTime,

        @Schema(description = "互动时间止（可空，含）")
        LocalDateTime endTime,

        @Schema(description = "页码（从 1 开始）", defaultValue = "1")
        Integer page,

        @Schema(description = "每页数量（默认 20，最大 100）", defaultValue = "20")
        Integer pageSize
) {
    public AdminInteractionListQuery {
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 20;
        } else if (pageSize > 100) {
            pageSize = 100;
        }
    }
}
