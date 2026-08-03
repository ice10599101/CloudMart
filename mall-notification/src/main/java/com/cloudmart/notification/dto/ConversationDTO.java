package com.cloudmart.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "会话DTO")
public record ConversationDTO(

    @Schema(description = "会话ID")
    Long id,

    @Schema(description = "用户1 ID")
    Long user1Id,

    @Schema(description = "用户2 ID")
    Long user2Id,

    @Schema(description = "最后一条消息")
    String lastMessage,

    @Schema(description = "最后消息时间")
    LocalDateTime lastMessageTime,

    @Schema(description = "当前用户未读数")
    Integer unreadCount,

    @Schema(description = "对方用户ID")
    Long otherUserId,

    @Schema(description = "创建时间")
    LocalDateTime createdAt
) {}
