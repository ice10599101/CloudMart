package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 宠物口吻提醒（代理 mall-notification 的 PET 类型通知；
 * 复用现有通知表与 WebSocket 推送，宠物只是"新的说话方式"）。
 */
@Schema(description = "宠物提醒")
public record PetReminderVO(
        Long notificationId,
        @Schema(description = "子类型: PET_BOTTLE_CAUGHT/PET_WORK_COMPLETED/...") String reminderType,
        String title,
        String content,
        Long bizId,
        Boolean isRead,
        LocalDateTime createdAt,
        @Schema(description = "优先级（原文档 §30）: P0 重要/P1 普通/P2 低（由提醒类型映射）") String priority
) {
}
