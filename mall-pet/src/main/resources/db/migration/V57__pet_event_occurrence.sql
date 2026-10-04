-- V57 (R33/§13.4): 限时活动期次——occurrence 驱动的窗口/快照/唯一领奖事实
-- 活动按期（occurrence）发布：复用 code 新一期不与历史期次混淆；
-- 领奖事实唯一 (pet_id, occurrence_id)；无窗口历史数据不证明当期达成。

CREATE TABLE IF NOT EXISTS `pet_event_occurrence` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '期次 ID（occurrenceId，雪花算法）',
    `event_code`     VARCHAR(64) NOT NULL COMMENT '活动 code（关联 pet_event_config）',
    `occurrence_index` INT NOT NULL COMMENT '第几期（同 code 递增，从 1 起）',
    `start_at`       DATETIME NOT NULL COMMENT '统计窗口开始(UTC)',
    `end_at`         DATETIME NOT NULL COMMENT '统计窗口结束(UTC)',
    `claim_deadline_at` DATETIME NOT NULL COMMENT '领奖截止(UTC，结束+宽限期)',
    `reward_snapshot` JSON DEFAULT NULL COMMENT '奖励快照（发布期次时冻结，配置后改不影响本期）',
    `status`         ENUM('ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE' COMMENT '期次状态',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_event_occurrence` (`id`),
    UNIQUE KEY `uk_event_occurrence` (`event_code`, `occurrence_index`),
    INDEX `idx_event_occurrence_window` (`event_code`, `start_at`, `end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='限时活动期次(R33)';

-- 领奖事实唯一 (pet_id, occurrence_id)：复用 code 新一期独立领奖，历史期次不覆盖
CREATE TABLE IF NOT EXISTS `pet_event_occurrence_claim` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `occurrence_id`  BIGINT UNSIGNED NOT NULL COMMENT '期次 ID',
    `event_code`     VARCHAR(64) NOT NULL COMMENT '活动 code（冗余，便于查询）',
    `pet_id`         BIGINT UNSIGNED NOT NULL COMMENT '领奖宠物（参与/达成事实归属）',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `reward_snapshot` JSON DEFAULT NULL COMMENT '领奖时的奖励快照',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_event_occurrence_claim` (`id`),
    UNIQUE KEY `uk_occurrence_claim` (`occurrence_id`, `pet_id`),
    INDEX `idx_occurrence_claim_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='限时活动期次领奖事实(R33)';
