-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V15
-- 模块: mall-wish
-- 说明: Sprint 2.6 同愿匹配 + 监督小队（文档 1.2 ⑧ / 十章 / 2.8）
--       wish_match_group   同愿小组（2-4 人，OPEN/FULL/CLOSED 状态机）
--       wish_match_member  组员（退出/被踢保留历史，仅 ACTIVE 参与唯一约束）
--       wish_match_config  匹配算法配置（权重/阈值/限频，管理后台实时生效）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (见文档第26章)
-- =============================================

-- ---------------------------------------------
-- ⑧ wish_match_group 同愿小组
-- keyword 为组主题（来自心愿标签/目标关键词）；city_code 为同城代理
-- （创建人活跃公开心愿 geohash 前 4 字符，约 39km 尺度，无城市名库
-- 时的同城判定实现，偏差已留档）；member_count 事务内维护，
-- 加组走 CAS UPDATE 防并发超卖名额。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_match_group` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `keyword`      VARCHAR(60) NOT NULL COMMENT '组主题关键词(来自心愿标签/目标)',
    `max_members`  TINYINT NOT NULL DEFAULT 4 COMMENT '小组容量(2-4人)',
    `wish_id`      BIGINT UNSIGNED DEFAULT NULL COMMENT '关联心愿ID(可空,自由建组)',
    `leader_id`    BIGINT UNSIGNED NOT NULL COMMENT '组长用户ID(创建者,退出自动转让)',
    `member_count` TINYINT NOT NULL DEFAULT 1 COMMENT '当前 ACTIVE 成员数(事务内维护)',
    `city_code`    VARCHAR(8) DEFAULT NULL COMMENT '同城代理(创建人活跃公开心愿 geohash 前缀4,可空)',
    `status`       ENUM('OPEN','FULL','CLOSED') NOT NULL DEFAULT 'OPEN' COMMENT '状态: 可加入/已满员/已关闭(解散或无人)',
    `closed_at`    DATETIME DEFAULT NULL COMMENT '关闭时间(UTC,解散或无人时写入)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_match_group` (`id`),
    INDEX `idx_group_scan` (`status`, `keyword`, `id`),
    INDEX `idx_group_leader` (`leader_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同愿小组(2-4人打卡监督)';

-- ---------------------------------------------
-- ⑧ wish_match_member 组员
-- 退出/被踢仅置 status=LEFT/KICKED 保留互动历史（文档验收：
-- 退出后互动历史保留不删除）。
-- 功能唯一索引: 同一用户在同一小组至多一条 ACTIVE 记录
-- （LEFT/KICKED 行不参与约束，允许退出后重新加入）。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_match_member` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `group_id`     BIGINT UNSIGNED NOT NULL COMMENT '小组ID',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `role`         ENUM('LEADER','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '角色(LEADER创建者可踢人/转让)',
    `status`       ENUM('ACTIVE','LEFT','KICKED') NOT NULL DEFAULT 'ACTIVE' COMMENT '成员状态(退出/被踢后保留历史仅标记)',
    `join_message` VARCHAR(200) DEFAULT NULL COMMENT '入组留言(可空)',
    `joined_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间(UTC)',
    `left_at`      DATETIME DEFAULT NULL COMMENT '退出/被踢时间(UTC)',
    PRIMARY KEY `pk_wish_match_member` (`id`),
    -- 功能唯一索引: 仅 ACTIVE 状态参与唯一性校验(同 V1 wish_interaction 模式)
    UNIQUE KEY `uk_member_active` (`group_id`, `user_id`, (IF(`status` = 'ACTIVE', 1, NULL))),
    INDEX `idx_member_user` (`user_id`, `status`),
    INDEX `idx_member_group` (`group_id`, `status`, `joined_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同愿小组成员(退出/被踢保留历史)';

-- ---------------------------------------------
-- wish_match_config 匹配算法配置
-- 管理后台修改后运行时实时生效（60s 快照缓存 + 更新主动失效），
-- 文档 2.6 验收：权重可配置，调整后结果排序变化不改代码。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_match_config` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `config_key`   VARCHAR(64) NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(500) NOT NULL COMMENT '配置值(字符串,业务层解析)',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '配置说明',
    `updated_by`   BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_match_config` (`id`),
    UNIQUE KEY `uk_match_config_key` (`config_key`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配算法配置(权重/阈值/限频,实时生效)';

-- ---------------------------------------------
-- 种子数据：匹配算法默认值（文档十章：关键词 0.4/城市 0.3/活跃度 0.3）
-- ---------------------------------------------
INSERT INTO `wish_match_config` (`id`, `config_key`, `config_value`, `description`) VALUES
    (1, 'match.weight_keyword', '0.4', '匹配权重-关键词(0-1,与城市/活跃度权重和建议为1,超和时按比例归一)'),
    (2, 'match.weight_city', '0.3', '匹配权重-城市/geohash同城(0-1)'),
    (3, 'match.weight_activity', '0.3', '匹配权重-小组成员活跃度(0-1)'),
    (4, 'match.score_threshold', '0.15', '推荐相似度阈值(0-1,低于阈值不推荐;精确关键词命中不受限)'),
    (5, 'match.remind_idle_days', '3', '互相提醒-组员多少天未活跃视为需提醒(天)'),
    (6, 'match.remind_daily_limit', '3', '互相提醒-每用户每日提醒上限(条)'),
    (7, 'match.create_daily_limit', '3', '建组-每用户每日建组上限(个)')
    ON DUPLICATE KEY UPDATE `config_key` = VALUES(`config_key`);
