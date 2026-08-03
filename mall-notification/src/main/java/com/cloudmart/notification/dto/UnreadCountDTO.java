package com.cloudmart.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "未读通知数量")
public record UnreadCountDTO(

    @Schema(description = "未读数量")
    Long count
) {}
