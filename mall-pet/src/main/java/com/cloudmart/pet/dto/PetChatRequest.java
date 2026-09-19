package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 宠物聊天请求。
 */
@Schema(description = "宠物聊天请求")
public record PetChatRequest(
        @Schema(description = "用户消息（1-500 字符）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "说点什么吧")
        @Size(max = 500, message = "消息最长 500 字符")
        String message
) {
}
