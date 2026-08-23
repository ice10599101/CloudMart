-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V13
-- 模块: mall-wish
-- 说明: Sprint 2.5 AI 心愿助手
--       ㊱b wish_ai_goal（AI 拆解目标，文档 1.2/2.11）
--       ⑰ wish_notification_preference（通知偏好矩阵，文档 1.2/2.14）
--       wish_ai_prompt（Prompt 模板版本管理 + A/B 分流，文档 2.5 管理后台）
--       wish_expected_at_action（预期管理选项埋点，文档 2.5 数据回收）
--       wish_ai_config（提醒策略全局配置，管理后台实时生效）
-- 注: ㊲c wish_ai_conversation 已在 V3 创建（scene 枚举已含
--     GOAL_BREAKDOWN/ANNUAL_REPORT），本迁移不重建。
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (见文档第26章)
-- =============================================

-- ---------------------------------------------
-- ㊱b wish_ai_goal AI 拆解目标
-- 用户在 AI 助手拆解结果中勾选的步骤持久化（status=PENDING），
-- 勾选进度更新走 PUT /wish/ai/goals/{goalId}
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_ai_goal` (
                                              `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
                                              `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                                              `wish_id`        BIGINT UNSIGNED DEFAULT NULL COMMENT '关联心愿ID(可空,允许无心愿的自由目标拆解)',
                                              `title`          VARCHAR(100) NOT NULL COMMENT '步骤标题',
    `description`    TEXT NOT NULL COMMENT '步骤描述',
    `estimated_days` INT NOT NULL DEFAULT 7 COMMENT '预计完成天数',
    `priority`       TINYINT NOT NULL DEFAULT 3 COMMENT '优先级(1-5, 1最高)',
    `status`         ENUM('PENDING','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '状态: 待开始/进行中/已完成/已取消',
    `ai_session_id`  VARCHAR(64) DEFAULT NULL COMMENT 'AI会话ID(关联wish_ai_conversation.session_id)',
    `started_at`     DATETIME DEFAULT NULL COMMENT '开始时间(UTC, IN_PROGRESS时写入)',
    `completed_at`   DATETIME DEFAULT NULL COMMENT '完成时间(UTC, COMPLETED时写入)',
    `deleted_at`     DATETIME DEFAULT NULL COMMENT '软删除时间',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_ai_goal` (`id`),
    INDEX `idx_ai_goal_user` (`user_id`, `status`, `created_at`),
    INDEX `idx_ai_goal_wish` (`wish_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI拆解目标(用户勾选的拆解步骤)';

-- ---------------------------------------------
-- ⑰ wish_notification_preference 通知偏好矩阵
-- 用户 × 通知类型(13类) × 渠道(4) 开关；无记录视为默认开启
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_notification_preference` (
                                                              `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
                                                              `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                                                              `notification_type` VARCHAR(30) NOT NULL COMMENT '通知类型: WISH_COMMENT/WISH_LIGHT/WISH_FULFILL/CAPSULE_OPEN/AI_REMINDER/CHECKIN_REMINDER/MATCH_RECOMMEND/BRAND_REWARD/ENCOUNTER_LETTER/DEVICE_OFFLINE/LEVEL_UP/BADGE_EARNED/SYSTEM',
    `channel`           ENUM('PUSH','SMS','EMAIL','IN_APP') NOT NULL COMMENT '通知渠道',
    `enabled`           TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否开启',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_notification_preference` (`id`),
    UNIQUE KEY `uk_preference_unique` (`user_id`, `notification_type`, `channel`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户通知偏好矩阵(无记录=默认开启)';

-- ---------------------------------------------
-- wish_ai_prompt Prompt 模板版本管理
-- 调整 Prompt 不改代码不重部署（文档 2.5：Prompt 管理）
-- A/B 分流：同 scene 下多条 ACTIVE 记录按 traffic_percent 加权随机
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_ai_prompt` (
                                                `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
                                                `scene`           ENUM('GOAL_BREAKDOWN','TREE_HOLE','ANNUAL_REPORT','EXPECTED_GUIDE') NOT NULL COMMENT 'AI场景: 目标拆解/树洞/年度报告/预期管理引导',
    `version`         INT NOT NULL COMMENT '版本号(scene内递增)',
    `name`            VARCHAR(100) NOT NULL COMMENT '模板名称(管理后台展示)',
    `content`         TEXT NOT NULL COMMENT 'Prompt正文(支持{placeholder}变量)',
    `ab_group`        ENUM('ALL','A','B') NOT NULL DEFAULT 'ALL' COMMENT 'A/B分组: ALL=不分流',
    `traffic_percent` TINYINT NOT NULL DEFAULT 100 COMMENT '流量百分比(1-100, 同scene的ACTIVE记录按此加权分流)',
    `status`          ENUM('DRAFT','ACTIVE','ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '状态: 草稿/生效中/已归档',
    `remark`          VARCHAR(255) DEFAULT NULL COMMENT '备注(版本变更说明)',
    `created_by`      BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人(管理后台用户ID)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_ai_prompt` (`id`),
    UNIQUE KEY `uk_prompt_scene_version` (`scene`, `version`),
    INDEX `idx_prompt_scene_status` (`scene`, `status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Prompt模板版本管理(A/B分流)';

-- ---------------------------------------------
-- wish_expected_at_action 预期管理选项埋点
-- 用户对"心愿到期"通知 3 选项的选择记录（转化率分析，文档 2.5 数据回收）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_expected_at_action` (
                                                         `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
                                                         `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                                                         `wish_id`    BIGINT UNSIGNED NOT NULL COMMENT '心愿ID',
                                                         `action`     ENUM('EXTEND','ADJUST','TO_CAPSULE') NOT NULL COMMENT '选择: 延长预期/AI调整目标/转入时间胶囊',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_wish_expected_at_action` (`id`),
    INDEX `idx_expected_action_user` (`user_id`, `created_at`),
    INDEX `idx_expected_action_wish` (`wish_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预期管理选项埋点(转化率分析)';

-- ---------------------------------------------
-- wish_ai_config AI/提醒策略全局配置
-- 管理后台修改后运行时实时生效（短 TTL 缓存），文档 2.5 管理后台
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_ai_config` (
                                                `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
                                                `config_key`   VARCHAR(64) NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(500) NOT NULL COMMENT '配置值(字符串,业务层解析)',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '配置说明',
    `updated_by`   BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_ai_config` (`id`),
    UNIQUE KEY `uk_ai_config_key` (`config_key`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI/提醒策略全局配置(管理后台实时生效)';

-- ---------------------------------------------
-- 种子数据：提醒策略默认值（管理后台可改）
-- ---------------------------------------------
INSERT INTO `wish_ai_config` (`id`, `config_key`, `config_value`, `description`) VALUES
                                                                                     (1, 'reminder.daily_limit', '1', '陪伴提醒单用户每日上限(条)'),
                                                                                     (2, 'reminder.quiet_start', '22:00', '免打扰时段开始(用户时区, HH:mm)'),
                                                                                     (3, 'reminder.quiet_end', '08:00', '免打扰时段结束(用户时区, HH:mm)'),
                                                                                     (4, 'expected.daily_limit', '3', '预期管理通知单用户每日上限(条)'),
                                                                                     (5, 'annual_report.ttl_hours', '168', '年度报告结果缓存时长(小时)')
    ON DUPLICATE KEY UPDATE `config_key` = VALUES(`config_key`);
