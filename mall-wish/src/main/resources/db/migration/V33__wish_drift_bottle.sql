-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V33
-- 模块: mall-wish
-- 说明: 漂流瓶（替代相遇信笺用户侧体验：投瓶/捞瓶，匿名随机漂流）
--       wish_drift_bottle              漂流瓶（投瓶人匿名，捞起者随机捞取）
--       wish_drift_bottle_interaction  漂流瓶匿名回应（祝福/点亮，每瓶每日 1 次）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- =============================================

-- ---------------------------------------------
-- wish_drift_bottle 漂流瓶
-- content 与 wish_id 二选一：自由匿名文字 / 关联我的公开心愿；
-- wish_title/wish_tags 为关联心愿快照（避免列表 N+1，且心愿后续变更不影响展示）；
-- status 状态机 FLOATING(漂流中) → PICKED(已被捞起)。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_drift_bottle` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `thrower_user_id` BIGINT UNSIGNED NOT NULL COMMENT '投瓶人用户ID(对捞起者匿名)',
    `content`         VARCHAR(500) DEFAULT NULL COMMENT '自由匿名文字(与wish_id二选一)',
    `wish_id`         BIGINT UNSIGNED DEFAULT NULL COMMENT '关联心愿ID(与content二选一)',
    `wish_title`      VARCHAR(200) DEFAULT NULL COMMENT '关联心愿标题快照(匿名展示)',
    `wish_tags`       JSON DEFAULT NULL COMMENT '关联心愿标签快照',
    `status`          ENUM('FLOATING','PICKED') NOT NULL DEFAULT 'FLOATING' COMMENT '状态:漂流中/已被捞起',
    `picker_user_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '捞起人用户ID',
    `thrown_at`       DATETIME NOT NULL COMMENT '投瓶时间(UTC)',
    `picked_at`       DATETIME DEFAULT NULL COMMENT '捞瓶时间(UTC)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_drift_bottle` (`id`),
    INDEX `idx_drift_bottle_float` (`status`, `thrower_user_id`),
    INDEX `idx_drift_bottle_thrower` (`thrower_user_id`, `id`),
    INDEX `idx_drift_bottle_picker` (`picker_user_id`, `picked_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='漂流瓶(匿名,海面漂流)';

-- ---------------------------------------------
-- wish_drift_bottle_interaction 漂流瓶匿名回应
-- 仅捞起人可回应自己捞起的关联心愿漂流瓶；uk(bottle,user,互动日)=每瓶每日 1 次；
-- BLESS 免费 / LIGHT 扣星光 2 并点亮对方心愿(light_count+1)。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_drift_bottle_interaction` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `bottle_id`     BIGINT UNSIGNED NOT NULL COMMENT '漂流瓶ID',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '回应发起者(捞起人)',
    `type`          ENUM('BLESS','LIGHT') NOT NULL COMMENT '匿名祝福/点亮对方心愿',
    `peer_wish_id`  BIGINT UNSIGNED NOT NULL COMMENT '对方心愿ID(LIGHT 时 light_count+1)',
    `interact_date` DATE NOT NULL COMMENT '互动日期(幂等键)',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_wish_drift_bottle_interaction` (`id`),
    UNIQUE KEY `uk_bottle_interact_daily` (`bottle_id`, `user_id`, `interact_date`),
    INDEX `idx_bottle_interact_user` (`user_id`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='漂流瓶匿名回应(祝福/点亮,每日1次)';