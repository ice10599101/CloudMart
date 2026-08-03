package com.cloudmart.notification.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "消息VO")
public record MessageVO(

    @Schema(description = "消息ID") Long id,
    @Schema(description = "会话ID") Long conversationId,
    @Schema(description = "发送者ID") Long senderId,
    @Schema(description = "发送者昵称") String senderNickname,
    @Schema(description = "发送者头像") String senderAvatar,
    @Schema(description = "消息内容") String content,
    @Schema(description = "消息类型") String type,
    @Schema(description = "是否撤回") Boolean isRecalled,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
