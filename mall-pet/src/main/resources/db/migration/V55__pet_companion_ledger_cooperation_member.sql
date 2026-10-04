-- V55 (R36 尾/R35 尾): 陪伴按宠物分账 + 合作周成员名额

-- 1) 陪伴分账（R36 §13.3）：用户日总上限保留（pet_companion_daily），
--    按宠物可审计明细——每个有效时间段同时更新用户总额与宠物明细，
--    pet.companionSeconds 只累计本宠实际 accepted，qualifiedDay 达到阈值才计陪伴日。
CREATE TABLE IF NOT EXISTS `pet_companion_daily_pet` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `pet_id`          BIGINT UNSIGNED NOT NULL COMMENT '宠物 ID（会话归属宠）',
    `business_date`   DATE NOT NULL COMMENT '业务日(Asia/Shanghai 00:00 重置)',
    `accepted_seconds` INT NOT NULL DEFAULT 0 COMMENT '本宠当日实际计入有效秒数（受账号日上限截断）',
    `granted_minutes` INT NOT NULL DEFAULT 0 COMMENT '本宠当日已产生的陪伴任务分钟数(floor 差值)',
    `qualified_day`   TINYINT NOT NULL DEFAULT 0 COMMENT '是否有效陪伴日（达到 qualifiedDayThresholdSeconds 才计连续天数）',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_companion_daily_pet` (`id`),
    UNIQUE KEY `uk_companion_daily_pet` (`pet_id`, `business_date`),
    INDEX `idx_companion_daily_pet_user` (`user_id`, `business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴按宠物日分账(R36)';

-- 2) 合作周成员名额（R35 §13.3）：UNIQUE(userId, week_start) 在接受时原子写入双方——
--    关闭"同人同周既当 inviter 又当 invitee"绕过双角色唯一键的组合缺口。
CREATE TABLE IF NOT EXISTS `pet_cooperation_member` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '成员用户 ID',
    `week_start`  DATE NOT NULL COMMENT '自然周起始(周一,北京时间)',
    `cooperation_id` BIGINT UNSIGNED NOT NULL COMMENT '所属合作实例 ID',
    `role`        ENUM('INVITER','INVITEE') NOT NULL COMMENT '加入角色',
    `pet_id`      BIGINT UNSIGNED NOT NULL COMMENT '参与时绑定宠物',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_cooperation_member` (`id`),
    UNIQUE KEY `uk_cooperation_member_user_week` (`user_id`, `week_start`),
    INDEX `idx_cooperation_member_cooperation` (`cooperation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作周成员名额(R35)';
