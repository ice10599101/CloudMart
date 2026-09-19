package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 留言墙留言请求（三期）。 */
@Schema(description = "留言墙留言请求")
public record PostWallMessageRequest(
        @Schema(description = "被留言的宠物 ID（房间主人）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请选择要留言的宠物")
        Long petId,

        @Schema(description = "留言内容（1-120 字）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "留言不能为空")
        @Size(max = 120, message = "留言最多 120 字")
        String content,

        @Schema(description = "心情标签（可选，展示用）")
        @Size(max = 10, message = "心情标签最多 10 字")
        String mood
) {
}
