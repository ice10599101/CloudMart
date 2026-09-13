package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 全站广播请求，与 mall-notification 服务端 BroadcastNotificationRequest 字段对齐。
 * body 传输：绕过 XssFilter 对 query 参数的 HTML 转义，并规避 URL 长度限制。
 */
public record BroadcastNotificationRequest(
    @NotBlank String type,
    @NotBlank @Size(max = 100) String title,
    @NotBlank @Size(max = 60000) String content
) {}
