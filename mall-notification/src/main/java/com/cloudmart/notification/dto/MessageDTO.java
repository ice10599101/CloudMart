package com.cloudmart.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "消息DTO")
public record MessageDTO(

    @Schema(description = "消息ID")
    Long id,

    @Schema(description = "会话ID")
    Long conversationId,

    @Schema(description = "发送者ID")
    Long senderId,

    @Schema(description = "消息内容")
    String content,

    @Schema(description = "消息类型")
    String type,

    @Schema(description = "是否撤回")
    Boolean isRecalled,

    @Schema(description = "创建时间")
    LocalDateTime createdAt
) {}
