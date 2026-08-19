package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 树洞治愈回复 VO（文档 2.11：POST /wish/ai/tree-hole 响应）。
 *
 * @param reply          AI 回复正文
 * @param sentimentScore 情感分数（-1.0~1.0，负值表示负面情绪；降级场景为 null）
 * @param resources      推荐资源（通常为空，情绪低落时 1-2 个）
 */
@Schema(description = "树洞治愈回复")
public record TreeHoleReplyVO(
        @Schema(description = "AI 回复正文") String reply,
        @Schema(description = "情感分数（-1.0~1.0）") Double sentimentScore,
        @Schema(description = "推荐资源") List<AiResourceVO> resources
) {
}
