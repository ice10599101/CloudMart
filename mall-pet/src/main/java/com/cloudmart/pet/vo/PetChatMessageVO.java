package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 宠物聊天消息。
 */
@Schema(description = "宠物聊天消息")
public record PetChatMessageVO(
        Long messageId,
        String role,
        String content,
        @Schema(description = "是否 AI 生成（false=固定行为/降级模板）") Boolean isAiReply,
        LocalDateTime createdAt
) {
}
