package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 主人回复留言请求（三期）：只有房间主人能回复自己墙上的留言。 */
@Schema(description = "主人回复留言请求")
public record ReplyWallMessageRequest(
        @Schema(description = "被回复的一级留言 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请选择要回复的留言")
        Long messageId,

        @Schema(description = "回复内容（1-120 字）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "回复不能为空")
        @Size(max = 120, message = "回复最多 120 字")
        String content
) {
}
