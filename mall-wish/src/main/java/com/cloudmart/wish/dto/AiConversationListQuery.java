package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.AiScene;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 对话历史查询（cursor 分页，文档 2.11：GET /wish/ai/conversations）。
 *
 * @param scene    场景筛选（默认 TREE_HOLE）
 * @param cursor   游标（上一页最后一条记录 id，可空）
 * @param pageSize 每页数量（默认 20，最大 50）
 */
@Schema(description = "AI 对话历史查询参数")
public record AiConversationListQuery(
        @Schema(description = "场景筛选（默认 TREE_HOLE）")
        AiScene scene,

        @Schema(description = "分页游标（上一页最后一条 id）")
        String cursor,

        @Schema(description = "每页数量（1-50，默认 20）")
        Integer pageSize
) {
    public AiScene safeScene() {
        return scene == null ? AiScene.TREE_HOLE : scene;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 50);
    }
}
