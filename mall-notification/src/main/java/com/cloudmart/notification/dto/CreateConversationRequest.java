package com.cloudmart.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "创建会话请求")
public record CreateConversationRequest(

    @NotNull @Schema(description = "对方用户ID")
    Long otherUserId
) {}
