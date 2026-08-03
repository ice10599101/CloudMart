package com.cloudmart.admin.dto.feign;

import java.time.LocalDateTime;

/**
 * 通知 Feign 传输对象，与 mall-notification 服务端 NotificationDTO 字段对齐
 */
public record NotificationDTO(
    Long id,
    Long userId,
    String type,
    String title,
    String content,
    Boolean isRead,
    Long bizId,
    String bizType,
    LocalDateTime createdAt
) {}
