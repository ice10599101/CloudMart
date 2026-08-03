package com.cloudmart.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "发送消息请求")
public record SendMessageRequest(

    @NotBlank @Size(max = 2000) @Schema(description = "消息内容")
    String content,

    @Schema(description = "消息类型: TEXT/IMAGE/PRODUCT")
    String type
) {}
