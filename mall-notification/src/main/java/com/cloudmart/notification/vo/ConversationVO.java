package com.cloudmart.notification.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "会话VO")
public record ConversationVO(

    @Schema(description = "会话ID") Long id,
    @Schema(description = "对方用户ID") Long otherUserId,
    @Schema(description = "对方用户昵称") String otherUserNickname,
    @Schema(description = "对方用户头像") String otherUserAvatar,
    @Schema(description = "最后一条消息") String lastMessage,
    @Schema(description = "最后消息时间") LocalDateTime lastMessageTime,
    @Schema(description = "未读消息数") Integer unreadCount
) {}
