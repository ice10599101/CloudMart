-- V20: 收尾补齐——B15/B16/B19/B21/N05/N06 缺口
-- 1) B16: 活动唯一物品已拥有时的固定替代星光（活动创建时快照）
ALTER TABLE `pet_event_config`
    ADD COLUMN `reward_alt_starlight` INT NOT NULL DEFAULT 20 COMMENT '唯一物品已拥有时的固定替代星光(0=不发)' AFTER `reward_item_code`;

-- 2) N05: 离线摘要确认游标（确认只推进游标，不删除真实事件）
CREATE TABLE IF NOT EXISTS `pet_offline_cursor` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `last_confirmed_at` DATETIME NOT NULL COMMENT '上次确认时间(UTC,摘要查询起点)',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_offline_cursor` (`id`),
    UNIQUE KEY `uk_offline_cursor_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='离线摘要确认游标(查询不重发奖励,确认只推进游标)';

-- 3) B19: 宠物通知偏好（免打扰/日常问候类型开关）
CREATE TABLE IF NOT EXISTS `pet_notify_pref` (
    `id`                     BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`                BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `mute_daily_greeting`    TINYINT NOT NULL DEFAULT 0 COMMENT '日常问候免打扰(1=静默)',
    `daily_greeting_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '日常问候类型开关(0=关闭该类通知)',
    `created_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_notify_pref` (`id`),
    UNIQUE KEY `uk_notify_pref_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物通知偏好(仅作用于日常问候,重要业务通知不受影响)';

-- 4) B21: 配置历史版本（每次管理端 upsert 自动快照，支持回退与审计）
CREATE TABLE IF NOT EXISTS `pet_config_version` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `config_type` VARCHAR(40) NOT NULL COMMENT '配置类型: job/study/career/furniture/equipment/skin/skill/evolution/event/daily_quest',
    `config_id`   BIGINT UNSIGNED NOT NULL COMMENT '配置行ID',
    `version`     INT NOT NULL COMMENT '版本号(同类型同配置递增)',
    `snapshot`    JSON NOT NULL COMMENT '整行快照(回退依据)',
    `operation`   ENUM('PUBLISH','ROLLBACK') NOT NULL COMMENT '操作类型',
    `operator`    VARCHAR(60) DEFAULT NULL COMMENT '操作管理员',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_config_version` (`id`),
    INDEX `idx_config_version` (`config_type`, `config_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置历史版本(发布快照/回退/审计)';
