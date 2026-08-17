-- =============================================
-- CloudMart 心愿宇宙模块 数据库初始化 V1
-- 模块: mall-wish
-- 说明: Sprint 1.1 必建表 (10张) + 分类/徽章种子数据
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (见文档第26章)
-- =============================================

-- ---------------------------------------------
-- ② wish_category 心愿分类字典
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_category` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`       VARCHAR(30) NOT NULL COMMENT '分类编码(唯一): CAREER/GROWTH/RELATION/TRAVEL/WEALTH/HOBBY',
    `name`       VARCHAR(60) NOT NULL COMMENT '分类名称',
    `sort`       INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `icon`       VARCHAR(255) DEFAULT NULL COMMENT '分类图标URL',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_category` (`id`),
    UNIQUE KEY `uk_category_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿分类字典';

-- ---------------------------------------------
-- ① wish 心愿主表
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '作者用户ID',
    `title`           VARCHAR(120) NOT NULL COMMENT '心愿标题',
    `description`     TEXT NOT NULL COMMENT '心愿描述',
    `category_id`     BIGINT UNSIGNED NOT NULL COMMENT '分类ID',
    `visibility`      ENUM('PUBLIC','PRIVATE','TREE_HOLE') NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性: PUBLIC公开/PRIVATE私密/TREE_HOLE树洞',
    `enable_ai_reply` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用AI回复: TREE_HOLE=true, 其他=false',
    `audit_strategy`  ENUM('LAZY','STRICT') NOT NULL DEFAULT 'LAZY' COMMENT '审核策略: LAZY先发后审/STRICT实时审核高危词',
    `trigger_env_emo` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否触发生命树情绪环境联动: 仅TREE_HOLE=true',
    `status`          ENUM('DRAFT','ACTIVE','OVERDUE','FULFILLING','FULFILLED','ARCHIVED') NOT NULL DEFAULT 'ACTIVE' COMMENT '心愿状态机',
    `fruit_type`      ENUM('GLOW','RESONANCE','BLOOM','SPARK') NOT NULL DEFAULT 'GLOW' COMMENT '果实类型: GLOW微光/RESONANCE共鸣/BLOOM绽放/SPARK星火',
    `expected_at`     DATETIME DEFAULT NULL COMMENT '预计完成时间(UTC绝对时间, 跨时区比对用)',
    `fulfilled_at`    DATETIME DEFAULT NULL COMMENT '实际还愿时间(UTC)',
    `geohash`         CHAR(7) DEFAULT NULL COMMENT 'LBS地理哈希(7位约153m, 仅PUBLIC, 模糊化后存储)',
    `media_urls`      JSON DEFAULT NULL COMMENT '媒体资源URL列表(OSS Key)',
    `tags`            JSON DEFAULT NULL COMMENT '标签列表(最多5个)',
    `same_wish_count` INT NOT NULL DEFAULT 0 COMMENT '同求计数(冗余, 避免count聚合)',
    `light_count`     INT NOT NULL DEFAULT 0 COMMENT '累计点亮数(含取消后回滚)',
    `bless_count`     INT NOT NULL DEFAULT 0 COMMENT '累计祝福数',
    `support_count`   INT GENERATED ALWAYS AS (`light_count` + `same_wish_count` + `bless_count`) STORED COMMENT '总互动数(生成列: 点亮+同求+祝福)',
    `audit_status`    ENUM('PENDING','APPROVED','REJECTED','AUTO_HIDDEN') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
    `is_visible`      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否对用户可见(与audit_status解耦, 便于先发后审)',
    `deleted_at`      DATETIME DEFAULT NULL COMMENT '软删除时间(核心业务数据物理删除禁止)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish` (`id`),
    INDEX `idx_wish_user` (`user_id`),
    INDEX `idx_wish_category` (`category_id`),
    INDEX `idx_wish_status` (`status`),
    INDEX `idx_wish_expected` (`expected_at`),
    INDEX `idx_wish_geohash` (`geohash`),
    INDEX `idx_wish_support` (`support_count`),
    INDEX `idx_wish_audit` (`audit_status`),
    INDEX `idx_wish_visibility` (`visibility`),
    INDEX `idx_wish_fruit_type` (`fruit_type`),
    INDEX `idx_wish_created` (`created_at`),
    INDEX `idx_wish_light` (`light_count`),
    INDEX `idx_wish_same_wish` (`same_wish_count`),
    INDEX `idx_wish_bless` (`bless_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿主表';

-- ---------------------------------------------
-- ③ wish_interaction 互动记录
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_interaction` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `wish_id`         BIGINT UNSIGNED NOT NULL COMMENT '心愿ID',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '互动用户ID',
    `type`            ENUM('LIGHT','SAME_WISH','BLESS','ANON_STAR') NOT NULL COMMENT '互动类型: LIGHT点亮/SAME_WISH同求/BLESS祝福/ANON_STAR匿名星光',
    `content`         VARCHAR(500) DEFAULT NULL COMMENT '互动内容(仅BLESS有文字)',
    `starlight_cost`  INT NOT NULL DEFAULT 0 COMMENT '本次消耗星光数',
    `deleted_at`      DATETIME DEFAULT NULL COMMENT '软删除时间(取消互动时置位, 保留审计轨迹)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_interaction` (`id`),
    -- 功能唯一索引: 非LIGHT类型且未删除时唯一(LIGHT可重复, 已删除不约束)
    UNIQUE KEY `uk_interaction_unique` (`wish_id`, `user_id`, `type`, (IF(`deleted_at` IS NULL AND `type` <> 'LIGHT', 1, NULL))),
    INDEX `idx_interaction_query` (`wish_id`, `type`, `created_at`),
    INDEX `idx_interaction_user` (`user_id`, `type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿互动记录';

-- ---------------------------------------------
-- ④ wish_growth_record 成长记录
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_growth_record` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `wish_id`         BIGINT UNSIGNED NOT NULL COMMENT '心愿ID',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '作者用户ID',
    `type`            ENUM('TEXT','IMAGE','VIDEO','DIARY') NOT NULL COMMENT '记录类型: TEXT纯文字/IMAGE图文/VIDEO视频/DIARY日记',
    `content`         TEXT NOT NULL COMMENT '记录内容(DIARY类型max 5000)',
    `media_urls`      JSON DEFAULT NULL COMMENT '媒体资源URL列表(OSS Key)',
    `progress_delta`  SMALLINT NOT NULL DEFAULT 0 COMMENT '本次进度增量(支持负值, -32768~32767)',
    `audit_status`    ENUM('PENDING','APPROVED','REJECTED','AUTO_HIDDEN') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
    `is_visible`      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可见',
    `deleted_at`      DATETIME DEFAULT NULL COMMENT '软删除时间(作者删除成长记录)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_growth_record` (`id`),
    INDEX `idx_growth_wish` (`wish_id`),
    INDEX `idx_growth_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿成长记录';

-- ---------------------------------------------
-- ⑤ wish_checkin 打卡
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_checkin` (
    `id`                 BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `wish_id`            BIGINT UNSIGNED NOT NULL COMMENT '心愿ID',
    `user_id`            BIGINT UNSIGNED NOT NULL COMMENT '打卡用户ID',
    `checkin_date`       DATE NOT NULL COMMENT '打卡日期(用户时区, 按日去重)',
    `content`            VARCHAR(500) DEFAULT NULL COMMENT '打卡内容(可空)',
    `is_makeup`          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否补卡(离线缓存超7天上传)',
    `starlight_granted`  TINYINT(1) NOT NULL DEFAULT 1 COMMENT '本次打卡是否已发放星光(补卡=false, 避免重复发放)',
    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_checkin` (`id`),
    UNIQUE KEY `uk_checkin_daily` (`wish_id`, `user_id`, `checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿打卡记录';

-- ---------------------------------------------
-- ⑥ wish_progress 心愿进度 (1:1 with wish)
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_progress` (
    `wish_id`         BIGINT UNSIGNED NOT NULL COMMENT '心愿ID(与wish一对一, 主键)',
    `current_value`   INT NOT NULL DEFAULT 0 COMMENT '当前进度值(支持任意单位: 步数/页数/天数)',
    `target_value`    INT NOT NULL DEFAULT 100 COMMENT '目标值(percentage = current_value / target_value * 100)',
    `current_streak`  INT NOT NULL DEFAULT 0 COMMENT '当前连续打卡天数(补卡中断, 重置为1)',
    `max_streak`      INT NOT NULL DEFAULT 0 COMMENT '历史最长连续天数(只增不减)',
    `version`         INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(防并发覆盖: 离线打卡/BLE数据重放)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_progress` (`wish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿进度(1:1 with wish)';

-- ---------------------------------------------
-- ⑩ wish_user_stat 用户心愿统计 (1:1 with mall_user.sys_user)
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_user_stat` (
    `user_id`            BIGINT UNSIGNED NOT NULL COMMENT '用户ID(与mall_user.sys_user一对一, 主键)',
    `timezone`           VARCHAR(32) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '用户时区(IANA, 用于AI提醒/环境切换/胶囊到期)',
    `level`              TINYINT NOT NULL DEFAULT 1 COMMENT '当前等级(1-5)',
    `level_title`        VARCHAR(30) NOT NULL DEFAULT '追梦新人' COMMENT '等级标题',
    `highest_level`      TINYINT NOT NULL DEFAULT 1 COMMENT '历史最高等级(等级只升不降的判定依据)',
    `starlight_balance`  INT NOT NULL DEFAULT 0 COMMENT '星光余额(冗余, 以wish_resource_log流水为最终事实来源)',
    `total_wishes`       INT NOT NULL DEFAULT 0 COMMENT '累计创建心愿数(含已软删, 只增不减)',
    `active_wishes`      INT NOT NULL DEFAULT 0 COMMENT '当前有效心愿数(软删-1)',
    `total_fulfilled`    INT NOT NULL DEFAULT 0 COMMENT '累计还愿数(历史事实, 不回退)',
    `total_helped`       INT NOT NULL DEFAULT 0 COMMENT '累计帮助他人次数(点亮+匿名星光)',
    `total_checkin_days` INT NOT NULL DEFAULT 0 COMMENT '累计打卡天数(含补卡, 历史事实记录)',
    `last_active_at`     DATETIME DEFAULT NULL COMMENT '最后活跃时间(UTC, 衰减判定依据)',
    `risk_score`         INT NOT NULL DEFAULT 0 COMMENT '风控分(驳回累计)',
    `is_restricted`      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被限制发布',
    `restricted_until`   DATETIME DEFAULT NULL COMMENT '限制解除时间(UTC)',
    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_user_stat` (`user_id`),
    INDEX `idx_stat_active` (`last_active_at`),
    INDEX `idx_stat_timezone` (`timezone`),
    INDEX `idx_stat_level` (`level`),
    INDEX `idx_stat_helped` (`total_helped`),
    INDEX `idx_stat_checkin` (`total_checkin_days`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户心愿统计(单表聚合避免实时count)';

-- ---------------------------------------------
-- ⑪ wish_resource_log 星光流水
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_resource_log` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `delta`         INT NOT NULL COMMENT '星光变化量(正=获取, 负=消耗)',
    `type`          ENUM('EARN','SPEND') NOT NULL COMMENT '流水类型: EARN获取/SPEND消耗',
    `source`        VARCHAR(30) NOT NULL COMMENT '来源: SIGNIN/LIGHTED/CHECKIN/FULFILL/LIGHT_OTHER/ANON_STAR/EXCHANGE',
    `ref_id`        BIGINT DEFAULT NULL COMMENT '关联业务ID(互动/打卡/还愿ID)',
    `balance_after` INT NOT NULL COMMENT '操作后余额快照',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_resource_log` (`id`),
    INDEX `idx_resource_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='星光流水(最终事实来源)';

-- ---------------------------------------------
-- ⑫ wish_badge 徽章定义
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_badge` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`       VARCHAR(30) NOT NULL COMMENT '徽章编码(唯一)',
    `name`       VARCHAR(60) NOT NULL COMMENT '徽章名称',
    `icon`       VARCHAR(255) NOT NULL COMMENT '徽章图标URL',
    `condition`  JSON DEFAULT NULL COMMENT '触发条件声明式定义(JSON)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_badge` (`id`),
    UNIQUE KEY `uk_badge_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿徽章定义';

-- ---------------------------------------------
-- ⑫b wish_user_badge 用户已获得徽章
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_user_badge` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `badge_id`   BIGINT UNSIGNED NOT NULL COMMENT '徽章ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_user_badge` (`id`),
    UNIQUE KEY `uk_user_badge` (`user_id`, `badge_id`),
    INDEX `idx_user_badge_badge` (`badge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户已获得徽章';

-- =============================================
-- 种子数据: 心愿分类 (6个系统预设)
-- =============================================
INSERT INTO `wish_category` (`id`, `code`, `name`, `sort`, `icon`) VALUES
    (1001, 'CAREER',   '学业事业', 1, NULL),
    (1002, 'GROWTH',   '成长改变', 2, NULL),
    (1003, 'RELATION', '情感关系', 3, NULL),
    (1004, 'TRAVEL',   '旅行梦想', 4, NULL),
    (1005, 'WEALTH',   '财富目标', 5, NULL),
    (1006, 'HOBBY',    '兴趣生活', 6, NULL)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

-- =============================================
-- 种子数据: 心愿徽章 (4个基础徽章, condition JSON声明式)
-- =============================================
INSERT INTO `wish_badge` (`id`, `code`, `name`, `icon`, `condition`) VALUES
    (2001, 'FIRST_WISH',      '第一次许愿',   '',
     JSON_OBJECT('type', 'WISH_CREATED', 'threshold', 1, 'description', '发布第一个心愿')),
    (2002, 'FIRST_FULFILL',   '第一次还愿',   '',
     JSON_OBJECT('type', 'WISH_FULFILLED', 'threshold', 1, 'description', '完成第一个还愿')),
    (2003, 'HELP_100',        '帮助100人',    '',
     JSON_OBJECT('type', 'TOTAL_HELPED', 'threshold', 100, 'description', '累计帮助100人(点亮+匿名星光)')),
    (2004, 'PERSIST_365',     '坚持365天',    '',
     JSON_OBJECT('type', 'TOTAL_CHECKIN_DAYS', 'threshold', 365, 'description', '累计打卡365天'))
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `condition` = VALUES(`condition`);
