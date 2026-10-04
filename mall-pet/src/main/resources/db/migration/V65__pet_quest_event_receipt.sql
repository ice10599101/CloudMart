-- V65 (R32/§13.4): 任务事件回执——进度事实可重建，禁止把历史行为加到今天
-- 任务进度由"事实回执"驱动：uk(quest_code, event_id) 保证同一业务事实至多消费一次；
-- source_time 记录事实发生时间，business_date 记录实际计入的业务日；
-- 历史事实仅在该日任务行已存在时补算（不为历史日凭空生成、不涌入今天）。

CREATE TABLE IF NOT EXISTS `pet_quest_event_receipt` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `pet_id`        BIGINT UNSIGNED NOT NULL COMMENT '宠物 ID（进度归属）',
    `quest_code`    VARCHAR(64) NOT NULL COMMENT '任务类型（PetQuestType.name，如 WORK/CHAT/COMPANION）',
    `event_id`      VARCHAR(128) NOT NULL COMMENT '业务事实唯一键（如 ACT_CLAIM:{activityId}/CHAT:{messageId}/COMPANION:{petId}:{date}:{minutes}）',
    `source_time`   DATETIME NOT NULL COMMENT '事实发生时间(UTC)',
    `business_date` DATE NOT NULL COMMENT '实际计入的业务日（事实日或补偿的历史日）',
    `amount`        INT NOT NULL COMMENT '本次计入进度',
    `status`        VARCHAR(20) NOT NULL DEFAULT 'APPLIED' COMMENT 'APPLIED 已计入 / SKIPPED_STALE 事实过期（当日任务行不存在）',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_quest_event_receipt` (`id`),
    UNIQUE KEY `uk_quest_event_receipt` (`quest_code`, `event_id`),
    INDEX `idx_quest_receipt_user` (`user_id`, `business_date`),
    INDEX `idx_quest_receipt_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务事件回执(R32)';
