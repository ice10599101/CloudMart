-- =============================================
-- V78: 生命树环境表漂移守卫（R29）
-- 背景: special-events 管理查询在运行库 503，而同域 configs 查询 200。
--   V10__wish_tree_env_ext.sql 为裸 ALTER + 建表，服务器库存在 Flyway
--   历史漂移先例（迁移被标记 success 但语句未全部生效，不会重放）。
--   本迁移对 V10 的三处结构变更逐一补守卫，空/漂移库重启后自愈：
--   ① wish_world_tree_state.season 列
--   ② wish_special_event 表（缺失会导致特殊事件列表查询失败）
--   ③ wish_env_config 表
-- 幂等：表用 CREATE TABLE IF NOT EXISTS；列用 information_schema 双判断。
-- =============================================

-- ① season 列守卫
SET @t_tree := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish_world_tree_state');
SET @c_season := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish_world_tree_state' AND COLUMN_NAME = 'season');
SET @ddl := IF(@t_tree = 1 AND @c_season = 0,
    'ALTER TABLE wish_world_tree_state ADD COLUMN season VARCHAR(16) NULL DEFAULT NULL COMMENT ''当前季节(SPRING/SUMMER/AUTUMN/WINTER,mall-job每日00:00按UTC日期扫描写入;NULL=未扫描,读取方实时计算兜底)'' AFTER environment',
    'SELECT ''跳过 season（表不存在或列已存在）'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ② wish_special_event 守卫（DDL 与 V10 保持一致）
CREATE TABLE IF NOT EXISTS `wish_special_event` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花ID)',
    `event_code`   VARCHAR(48) NOT NULL COMMENT '事件代码(关联wish_env_config.env_code,如METEOR_SHOWER/AURORA/STAR_NIGHT)',
    `title`        VARCHAR(64) NOT NULL COMMENT '事件标题(管理端展示)',
    `description`  VARCHAR(255) DEFAULT NULL COMMENT '事件描述(管理端展示)',
    `status`       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE活跃/ENDED已结束;过期未标记读取方惰性判定)',
    `triggered_by` BIGINT UNSIGNED NOT NULL COMMENT '触发管理员用户ID(审计)',
    `triggered_at` DATETIME NOT NULL COMMENT '触发时间(UTC,全站同步展示起点)',
    `expires_at`   DATETIME DEFAULT NULL COMMENT '过期时间(UTC;NULL=持续至手动结束)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_special_event` (`id`),
    KEY `idx_special_event_status` (`status`, `triggered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='世界生命树全站特殊事件(Sprint2.2,流星雨/极光/星辰夜)';

-- ③ wish_env_config 守卫（DDL 与 V10 保持一致；种子由 V11 修复脚本负责，此处不补）
CREATE TABLE IF NOT EXISTS `wish_env_config` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花ID)',
    `env_code`    VARCHAR(48) NOT NULL COMMENT '环境代码(SUNNY/RAIN/SNOW/CLOUDY/RAINBOW/SPRING/.../METEOR_SHOWER;唯一)',
    `category`    VARCHAR(16) NOT NULL COMMENT '环境分类(WEATHER天气/SEASON季节/TIME时段/SPECIAL_EVENT特殊事件;仅管理端分组展示)',
    `name`        VARCHAR(64) NOT NULL COMMENT '环境名称(如流星雨/中秋)',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '环境描述(管理端展示)',
    `priority`    INT NOT NULL DEFAULT 0 COMMENT '渲染优先级(数值大者优:SPECIAL_EVENT=100/MOOD=80/WEATHER=50/SEASON=30/TIME=10)',
    `visual`      JSON DEFAULT NULL COMMENT '四端渲染视觉参数JSON(crownColor/skyColor/particle/lightCoreColor等,前端透传消费)',
    `is_active`   TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用(0=下架,读取方过滤)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_env_config` (`id`),
    UNIQUE KEY `uk_env_code` (`env_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='世界生命树环境配置(表化,Sprint2.2新增环境仅插行不改代码)';
