package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 树洞消息请求（文档 2.11：POST /wish/ai/tree-hole）。
 *
 * @param wishId  树洞心愿 ID
 * @param message 倾诉内容（≤2000 字）
 */
@Schema(description = "树洞消息请求")
public record TreeHoleMessageRequest(
        @Schema(description = "树洞心愿 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "心愿 ID 不能为空")
        Long wishId,

        @Schema(description = "倾诉内容", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "倾诉内容不能为空")
        @Size(max = 2000, message = "倾诉内容最长 2000 字")
        String message
) {
}
