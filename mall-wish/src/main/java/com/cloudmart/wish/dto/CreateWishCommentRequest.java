package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发表心愿评论请求（文档 2.2 节）。
 *
 * @param content   评论内容（必填，≤500 字符，入库前 XSS 转义 + 敏感词标记）
 * @param parentId  父评论 ID（回复时必传；顶级评论为 null；仅支持二级回复，
 *                  回复子评论自动挂载到其顶级评论下）
 */
@Schema(description = "发表心愿评论请求")
public record CreateWishCommentRequest(
        @Schema(description = "评论内容", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "评论内容不能为空")
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 500, message = "评论内容不能超过 500 字符")
        String content,

        @Schema(description = "父评论 ID（回复时传入）")
        Long parentId
) {
}
