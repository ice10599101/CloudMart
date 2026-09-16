package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 漂流瓶评论请求。
 *
 * @param content     评论内容（纯文本，最长 500 字）
 * @param parentId    被回复的评论 ID（顶级评论不传；必须是同一漂流瓶下的评论）
 * @param isAnonymous 是否匿名评论（默认 true；false 实名时展示真实昵称头像）
 */
@Schema(description = "漂流瓶评论请求")
public record BottleCommentRequest(
        @Schema(description = "评论内容（纯文本，最长 500 字）")
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 500, message = "评论内容最长 500 字")
        String content,

        @Schema(description = "被回复的评论 ID（顶级评论不传）")
        Long parentId,

        @Schema(description = "是否匿名评论（默认 true）")
        Boolean isAnonymous
) {
}