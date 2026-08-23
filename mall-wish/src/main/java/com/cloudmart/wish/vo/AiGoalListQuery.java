package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.GoalStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 我的 AI 目标列表查询（cursor 分页，Sprint 2.5）。
 *
 * @param status   状态筛选（可空=全部）
 * @param wishId   心愿筛选（可空）
 * @param cursor   游标（上一页最后一条 id，可空）
 * @param pageSize 每页数量（默认 20，最大 50）
 */
@Schema(description = "我的 AI 目标列表查询参数")
public record AiGoalListQuery(
        @Schema(description = "状态筛选（可空=全部）")
        GoalStatus status,

        @Schema(description = "心愿 ID 筛选（可空）")
        Long wishId,

        @Schema(description = "分页游标（上一页最后一条 id）")
        String cursor,

        @Schema(description = "每页数量（1-50，默认 20）")
        Integer pageSize
) {
    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 50);
    }
}
