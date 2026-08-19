package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 互动列表查询（cursor 分页，文档 2.2 节）。
 *
 * @param type     互动类型筛选（可空）
 * @param cursor   游标（上一页最后一条记录 id，可空）
 * @param pageSize 每页数量（默认 20，最大 50）
 */
@Schema(description = "互动列表查询参数")
public record InteractionListQuery(
        @Schema(description = "互动类型筛选")
        InteractionType type,

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
