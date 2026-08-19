package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.AiConversationRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 对话记录 VO（文档 2.11：GET /wish/ai/conversations 列表项）。
 *
 * @param id             记录 ID
 * @param role           角色：USER / ASSISTANT
 * @param content        消息内容
 * @param sentimentScore 情感分数（-1.0~1.0，仅 ASSISTANT 记录，可空）
 * @param resources      推荐资源（仅 ASSISTANT 记录）
 * @param createdAt      创建时间（UTC）
 */
@Schema(description = "AI 对话记录")
public record AiConversationVO(
        @Schema(description = "记录 ID") Long id,
        @Schema(description = "角色") AiConversationRole role,
        @Schema(description = "消息内容") String content,
        @Schema(description = "情感分数（-1.0~1.0）") Double sentimentScore,
        @Schema(description = "推荐资源") List<AiResourceVO> resources,
        @Schema(description = "创建时间（UTC）") LocalDateTime createdAt
) {
}
