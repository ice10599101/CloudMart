package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发送通知请求，与 mall-notification 服务端 SendNotificationRequest 字段对齐
 */
public record SendNotificationRequest(
    @NotNull Long userId,
    @NotBlank @Size(max = 30) String type,
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 1000) String content,
    Long bizId,
    @Size(max = 30) String bizType
) {}
