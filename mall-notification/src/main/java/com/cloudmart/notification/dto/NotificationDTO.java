package com.cloudmart.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "通知DTO")
public record NotificationDTO(

    @Schema(description = "通知ID")
    Long id,

    @Schema(description = "用户ID")
    Long userId,

    @Schema(description = "通知类型")
    String type,

    @Schema(description = "通知标题")
    String title,

    @Schema(description = "通知内容")
    String content,

    @Schema(description = "是否已读")
    Boolean isRead,

    @Schema(description = "关联业务ID")
    Long bizId,

    @Schema(description = "关联业务类型")
    String bizType,

    @Schema(description = "创建时间")
    LocalDateTime createdAt
) {}
