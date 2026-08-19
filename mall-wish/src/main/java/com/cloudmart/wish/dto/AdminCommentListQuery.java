package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.WishCommentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理后台评论列表 offset 分页查询参数（Sprint 1.2）。
 *
 * <p>敏感词审核场景：sensitiveHit=true + status=VISIBLE 筛选待处理命中列表。</p>
 */
@Schema(name = "AdminCommentListQuery", description = "管理后台评论列表查询参数")
public record AdminCommentListQuery(

        @Schema(description = "心愿 ID 筛选（可空）")
        Long wishId,

        @Schema(description = "评论用户 ID 筛选（可空）")
        Long userId,

        @Schema(description = "敏感词命中筛选（可空：true=仅命中，false=仅未命中）")
        Boolean sensitiveHit,

        @Schema(description = "状态筛选（可空）")
        WishCommentStatus status,

        @Schema(description = "页码（从 1 开始）", defaultValue = "1")
        Integer page,

        @Schema(description = "每页数量（默认 20，最大 100）", defaultValue = "20")
        Integer pageSize
) {
    public AdminCommentListQuery {
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
