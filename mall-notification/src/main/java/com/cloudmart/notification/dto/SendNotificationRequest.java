package com.cloudmart.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "发送通知请求")
public record SendNotificationRequest(

    @NotNull @Schema(description = "用户ID")
    Long userId,

    @NotBlank @Size(max = 30) @Schema(description = "通知类型")
    String type,

    @NotBlank @Size(max = 200) @Schema(description = "通知标题")
    String title,

    @NotBlank @Size(max = 1000) @Schema(description = "通知内容")
    String content,

    @Schema(description = "关联业务ID")
    Long bizId,

    @Size(max = 30) @Schema(description = "关联业务类型")
    String bizType
) {}
