-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V4
-- 模块: mall-wish
-- 说明: Sprint 2.2 生命树情绪环境联动（进度待办 ①）
--       ㊳ wish_world_tree_state（世界生命树全局环境状态，单行表）
-- 决策: 1. environment 采用 VARCHAR(32) 而非 ENUM——文档 Sprint 2.2 要求
--          "环境配置表化，新增环境不改代码"，ENUM 加值需 ALTER TABLE；
--          当前代码层仅使用 SUNNY/RAIN/RAINBOW（情绪联动），季节/天气/
--          特殊事件（SPRING/SUMMER/AUTUMN/WINTER 等）由后续 Sprint 扩展。
--       2. mood_score 不落库（文档 2.2 隐私保护：避免长期留存用户情绪
--          聚合数据），仅存 Redis wish:tree:mood（TTL 10 分钟）；
--          sample_count 仅为聚合计数，不含任何情绪明细，可落库用于观测。
--       3. 单行表（id 固定 1）：世界树环境为全站全局状态，无多行语义。
-- ID策略: 固定主键 1（业务层保证单行，非雪花）
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (见文档第26章)
-- =============================================

-- ---------------------------------------------
-- wish_world_tree_state 世界生命树全局环境状态（单行）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_world_tree_state` (
    `id`                 TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '固定单行主键(恒为1)',
    `environment`        VARCHAR(32) NOT NULL DEFAULT 'SUNNY' COMMENT '当前环境(SUNNY/RAIN/RAINBOW;预留季节/天气/特殊事件扩展值)',
    `environment_source` VARCHAR(32) NOT NULL DEFAULT 'INIT' COMMENT '最近一次环境变更来源(INIT/MOOD_RAIN/MOOD_RAIN_RENEW/MOOD_RAINBOW/BLESS_BURST_RAINBOW/RAINBOW_EXPIRED/MOOD_RECOVER)',
    `triggered_at`       DATETIME DEFAULT NULL COMMENT '当前环境触发时间(UTC;RAIN续雨时保持首次触发时间作为最短持续基准)',
    `expires_at`         DATETIME DEFAULT NULL COMMENT '当前环境过期时间(UTC;NULL=无固定过期,RAIN持续至扫描复评)',
    `last_scan_at`       DATETIME DEFAULT NULL COMMENT '最近一次情绪扫描时间(UTC)',
    `sample_count`       INT NOT NULL DEFAULT 0 COMMENT '最近一次扫描聚合样本数(仅计数,无情绪明细)',
    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_world_tree_state` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='世界生命树全局环境状态(单行,情绪联动文档2.2/Sprint2.2)';
