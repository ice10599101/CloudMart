-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V21
-- 模块: mall-wish
-- 说明: Sprint 3.5 社区活动系统（文档 2.21/3.5）
--       wish_activity                 活动配置（配置表化，状态机 DRAFT→ACTIVE→ENDED→ARCHIVED）
--       wish_activity_participant     参与记录（普通参与 + 合伙人申请/审批，uk 防重复）
--       wish_activity_reward_log      奖励发放日志（幂等：uk 活动×用户×奖励类型）
--       活动进度 Redis 原子计数：activity:progress:{activityId}（INCR）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- =============================================

-- ---------------------------------------------
-- ㉜ wish_activity 活动配置
-- type 四类：WORLD_EVENT 世界事件 / FESTIVAL 节日活动 / CITY 城市活动 /
-- WISH_PARTNER 心愿合伙人；condition JSON 触发规则（如
-- {"type":"PROGRESS_COUNTER","threshold":100}）；reward JSON
-- （如 {"starlight":100,"badgeCode":"COLLABORATOR"}）；
-- cityCode 仅城市活动使用（按城市过滤展示）。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_activity` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `type`           ENUM('WORLD_EVENT','FESTIVAL','CITY','WISH_PARTNER') NOT NULL COMMENT '活动类型',
    `title`          VARCHAR(120) NOT NULL COMMENT '活动标题',
    `description`    VARCHAR(1000) DEFAULT NULL COMMENT '活动描述/规则说明',
    `cover_image`    VARCHAR(500) DEFAULT NULL COMMENT '封面图(节日氛围装饰)',
    `condition_json` JSON DEFAULT NULL COMMENT '触发条件 JSON(类型/阈值)',
    `reward_json`    JSON DEFAULT NULL COMMENT '奖励配置 JSON(星光/徽章)',
    `city_code`      CHAR(4) DEFAULT NULL COMMENT '城市代理(geohash4,城市活动专用)',
    `status`         ENUM('DRAFT','ACTIVE','ENDED','ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '状态机: 筹备/进行中/结束/归档',
    `valid_from`     DATETIME DEFAULT NULL COMMENT '展示开始(UTC,NULL=不限)',
    `valid_to`       DATETIME DEFAULT NULL COMMENT '展示结束(UTC,NULL=不限;到期入口消失但详情仍可访问)',
    `progress_counter` BIGINT NOT NULL DEFAULT 0 COMMENT '进度计数(Redis INCR 周期回写镜像)',
    `created_by`     BIGINT UNSIGNED NOT NULL COMMENT '创建管理员',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_activity` (`id`),
    INDEX `idx_activity_status` (`status`, `type`, `valid_from`),
    INDEX `idx_activity_city` (`city_code`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区活动配置(配置表化,状态机)';

-- ---------------------------------------------
-- ㉝ wish_activity_participant 参与记录
-- 普通活动：join 即 APPROVED（进度 Redis INCR）；心愿合伙人：申请
-- (PENDING, 携 wishId+skills) → 作者审批（APPROVED 进组/REJECTED）；
-- uk(activity,user) 防重复参与/重复申请。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_activity_participant` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `activity_id`  BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '参与用户',
    `role`         ENUM('LEADER','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '角色(合伙人招募作者=LEADER)',
    `status`       ENUM('PENDING','APPROVED','REJECTED','JOINED') NOT NULL DEFAULT 'JOINED' COMMENT '状态(合伙人申请 PENDING;普通参与 JOINED)',
    `wish_id`      BIGINT UNSIGNED DEFAULT NULL COMMENT '协作心愿ID(合伙人申请提交)',
    `skills`       JSON DEFAULT NULL COMMENT '技能标签 JSON(如 ["design","video"])',
    `match_score`  INT NOT NULL DEFAULT 0 COMMENT '技能匹配度(0-100,与招募需求交集占比)',
    `applied_at`   DATETIME DEFAULT NULL COMMENT '申请时间(UTC)',
    `reviewed_at`  DATETIME DEFAULT NULL COMMENT '审批时间(UTC)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_activity_participant` (`id`),
    UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`),
    INDEX `idx_participant_activity` (`activity_id`, `status`),
    INDEX `idx_participant_user` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动参与/合伙人申请记录';

-- ---------------------------------------------
-- wish_activity_reward_log 奖励发放日志（审计 + 幂等）
-- uk(activity,user,reward_type) —— 重复发放返回"已发放"（幂等验收）。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_activity_reward_log` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `activity_id`  BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '获奖励用户',
    `reward_type`  ENUM('STARLIGHT','BADGE') NOT NULL COMMENT '奖励类型',
    `amount`       INT NOT NULL DEFAULT 0 COMMENT '数量(星光数;徽章恒 1)',
    `ref_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '关联 ID(徽章 ID)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间(UTC)',
    PRIMARY KEY `pk_wish_activity_reward_log` (`id`),
    UNIQUE KEY `uk_reward_unique` (`activity_id`, `user_id`, `reward_type`),
    INDEX `idx_reward_activity` (`activity_id`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动奖励发放日志(幂等+审计)';
