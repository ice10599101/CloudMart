-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V16
-- 模块: mall-wish
-- 说明: Sprint 2.7 还愿传承 + 内容生态 + 排行榜（文档 2.7/2.9/2.8）
--       wish_fulfillment_inherit  传承推送记录（作者定向推送给曾同求用户）
--       wish_content_flow_log     内容流转日志（还愿 → community 帖子）
--       wish_leaderboard_config   排行榜配置（刷新周期/Top N/同分处理）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (见文档第26章)
-- =============================================

-- ---------------------------------------------
-- wish_fulfillment_inherit 传承推送记录
-- 作者对 FULFILLED 心愿发起传承：定向推送给曾 SAME_WISH（同求）的用户，
-- 通知含还愿故事摘要；target_count 快照当时同求人数（触达率分母）。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_fulfillment_inherit` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `wish_id`         BIGINT UNSIGNED NOT NULL COMMENT '心愿ID',
    `fulfillment_id`  BIGINT UNSIGNED NOT NULL COMMENT '还愿记录ID',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '发起传承的用户(心愿作者)',
    `target_count`    INT NOT NULL DEFAULT 0 COMMENT '快照:当时同求(SAME_WISH)用户数',
    `pushed_count`    INT NOT NULL DEFAULT 0 COMMENT '实际推送成功数',
    `message`         VARCHAR(500) DEFAULT NULL COMMENT '作者附言(可空,≤500字)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_wish_fulfillment_inherit` (`id`),
    UNIQUE KEY `uk_inherit_fulfillment` (`fulfillment_id`),
    INDEX `idx_inherit_user` (`user_id`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='还愿传承推送记录(同求用户定向通知)';

-- ---------------------------------------------
-- wish_content_flow_log 内容流转日志
-- 还愿成功后异步生成 community 帖子（《我的梦想实现记录》图文模板），
-- Feign 失败重试 3 次仍失败记 FAILED（管理端可重试）；还愿故事删除时
-- 同步隐藏帖子记 HIDDEN（状态同步规则见内容流转映射文档）。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_content_flow_log` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `wish_id`        BIGINT UNSIGNED NOT NULL COMMENT '心愿ID',
    `fulfillment_id` BIGINT UNSIGNED NOT NULL COMMENT '还愿记录ID',
    `post_id`        BIGINT UNSIGNED DEFAULT NULL COMMENT 'community 帖子ID(成功后回填)',
    `status`         ENUM('SUCCESS','FAILED','HIDDEN') NOT NULL DEFAULT 'FAILED' COMMENT '流转状态',
    `retry_count`    INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `error_msg`      VARCHAR(500) DEFAULT NULL COMMENT '最近一次失败原因',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_content_flow_log` (`id`),
    UNIQUE KEY `uk_flow_fulfillment` (`fulfillment_id`),
    INDEX `idx_flow_status` (`status`, `created_at`),
    INDEX `idx_flow_wish` (`wish_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='还愿内容流转日志(wish→community)';

-- ---------------------------------------------
-- wish_leaderboard_config 排行榜配置
-- 数据源固定：HOT=wish.light_count / WARM=wish.bless_count /
-- PERSISTENCE=wish_user_stat.total_checkin_days / SPARK=total_helped；
-- 管理端可调刷新周期/Top N/同分处理/封禁过滤。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_leaderboard_config` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `config_key`   VARCHAR(64) NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(200) NOT NULL COMMENT '配置值(字符串,业务层解析)',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '配置说明',
    `updated_by`   BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_leaderboard_config` (`id`),
    UNIQUE KEY `uk_leaderboard_config_key` (`config_key`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排行榜配置(计算周期/Top N/同分处理)';

-- ---------------------------------------------
-- 种子数据（文档 2.7：每 10 分钟刷新 + Top 100 + 同分按 created_at）
-- ---------------------------------------------
INSERT INTO `wish_leaderboard_config` (`id`, `config_key`, `config_value`, `description`) VALUES
    (1, 'lb.refresh_minutes', '10', '榜单刷新周期(分钟,ZSet 全量重建)'),
    (2, 'lb.top_size', '100', '每榜单保留 Top N'),
    (3, 'lb.tiebreak', 'CREATED_AT_ASC', '同分处理: CREATED_AT_ASC(早在前)/CREATED_AT_DESC'),
    (4, 'lb.exclude_restricted', '1', '是否排除风控受限用户(1=排除,安全验收:不展示被封禁用户)')
    ON DUPLICATE KEY UPDATE `config_key` = VALUES(`config_key`);
