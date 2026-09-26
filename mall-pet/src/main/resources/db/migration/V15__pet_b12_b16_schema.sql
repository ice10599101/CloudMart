-- V15: B12–B16 整改 schema（家园/社交/每日任务/限时活动）
-- 1) 房间点赞持久化（B13：替换"30 天 Redis 键但文案说次日可赞"的矛盾）
CREATE TABLE IF NOT EXISTS `pet_room_like` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '点赞用户ID',
    `room_id`    BIGINT UNSIGNED NOT NULL COMMENT '房间ID',
    `active`     TINYINT NOT NULL DEFAULT 1 COMMENT '当前是否点赞(取消置0;曾获奖标记保留防重复发奖)',
    `rewarded`   TINYINT NOT NULL DEFAULT 0 COMMENT '是否已发过点赞经验(取消再点不再发奖)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次点赞时间(UTC)',
    PRIMARY KEY `pk_pet_room_like` (`id`),
    UNIQUE KEY `uk_room_like` (`user_id`, `room_id`),
    INDEX `idx_room_like_room` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房间点赞持久化(唯一点赞,显式取消)';

-- 2) 用户级屏蔽名单（B14：屏蔽后禁止新增拜访收益/挑战/留言/申请）
CREATE TABLE IF NOT EXISTS `pet_user_block` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '发起屏蔽的用户ID',
    `blocked_user_id` BIGINT UNSIGNED NOT NULL COMMENT '被屏蔽的用户ID',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '屏蔽时间(UTC)',
    PRIMARY KEY `pk_pet_user_block` (`id`),
    UNIQUE KEY `uk_user_block` (`user_id`, `blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户屏蔽名单(双向禁止互动收益)';

-- 3) 举报（B14：举报原因/处理状态/管理员审计）
CREATE TABLE IF NOT EXISTS `pet_report` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `reporter_user_id` BIGINT UNSIGNED NOT NULL COMMENT '举报人用户ID',
    `target_type`      ENUM('WALL_MESSAGE','BOTTLE_CONTENT','NICKNAME') NOT NULL COMMENT '举报对象类型',
    `target_id`        BIGINT UNSIGNED NOT NULL COMMENT '举报对象ID',
    `reason`           VARCHAR(200) NOT NULL COMMENT '举报原因(用户提交)',
    `status`           ENUM('PENDING','HANDLED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '处理状态',
    `handled_by`       BIGINT UNSIGNED DEFAULT NULL COMMENT '处理管理员ID',
    `handled_at`       DATETIME DEFAULT NULL COMMENT '处理时间(UTC)',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间(UTC)',
    PRIMARY KEY `pk_pet_report` (`id`),
    INDEX `idx_pet_report_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物内容举报(管理员处理审计)';

-- 4) 宠物关系规范化无向身份（B14：A→B 与 B→A 不再是两条 ACTIVE）
ALTER TABLE `pet_relation`
    ADD COLUMN `pet_a_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '规范化较小宠物ID' AFTER `to_pet_id`,
    ADD COLUMN `pet_b_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '规范化较大宠物ID' AFTER `pet_a_id';

UPDATE `pet_relation`
SET `pet_a_id` = LEAST(`from_pet_id`, `to_pet_id`),
    `pet_b_id` = GREATEST(`from_pet_id`, `to_pet_id`);

ALTER TABLE `pet_relation`
    MODIFY COLUMN `pet_a_id` BIGINT UNSIGNED NOT NULL COMMENT '规范化较小宠物ID',
    MODIFY COLUMN `pet_b_id` BIGINT UNSIGNED NOT NULL COMMENT '规范化较大宠物ID';

-- 重复无向 ACTIVE（迁移前可能存在 A→B 与 B→A 两条）：保留最早一条，其余进入待处理并留审计
INSERT INTO `pet_migration_conflict` (`conflict_type`, `entity_table`, `entity_id`, `user_id`, `detail`)
SELECT 'RELATION_OVERFLOW', 'pet_relation', MIN(r.`id`), NULL,
       JSON_OBJECT('petA', r.`pet_a_id`, 'petB', r.`pet_b_id`, 'relType', r.`rel_type`,
                   'ids', JSON_ARRAYAGG(r.`id`), 'action', 'KEEP_EARLIEST_OTHERS_CANCELLED')
FROM `pet_relation` r
WHERE r.`status` = 'ACTIVE'
GROUP BY r.`pet_a_id`, r.`pet_b_id`, r.`rel_type`
HAVING COUNT(*) > 1;

UPDATE `pet_relation` a
JOIN (
    SELECT ranked.id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY pet_a_id, pet_b_id, rel_type, status ORDER BY id ASC) AS rn
        FROM `pet_relation`
        WHERE `status` = 'ACTIVE'
    ) ranked
    WHERE ranked.rn > 1
) dup ON a.`id` = dup.`id`
SET a.`status` = 'CANCELLED';

ALTER TABLE `pet_relation`
    ADD UNIQUE KEY `uk_pet_relation_pair` (`pet_a_id`, `pet_b_id`, `rel_type`, (IF(`status` = 'ACTIVE', 1, NULL)));

-- 5) 每日任务：CANCELLED 状态（B15：停用任务显式取消且不阻挡宝箱）
ALTER TABLE `pet_daily_quest`
    MODIFY COLUMN `status` ENUM('IN_PROGRESS','COMPLETE','CLAIMED','EXPIRED','CANCELLED') NOT NULL
        COMMENT '状态机:进行中/已完成/已领取/已过期/已取消(停用配置显式取消,不阻挡宝箱)';

-- 6) 限时活动显式模式（B16：LIFETIME 累计 / WINDOW 限时，不再靠时间字段猜测）
ALTER TABLE `pet_event_config`
    ADD COLUMN `event_mode` ENUM('LIFETIME','WINDOW') NOT NULL DEFAULT 'LIFETIME'
        COMMENT '活动模式:LIFETIME累计(原无时间活动)/WINDOW限时(按[startsAt,endsAt)统计,结束后24h可领)' AFTER `enabled`;

-- 7) 家园重复摆放历史冲突（B13：保留最早合法实例，其余归档并重算舒适度）
ALTER TABLE `pet_room_item`
    ADD COLUMN `archived_at` DATETIME DEFAULT NULL COMMENT '归档时间(重复摆放修复,UTC)' AFTER `created_at`;

INSERT INTO `pet_migration_conflict` (`conflict_type`, `entity_table`, `entity_id`, `user_id`, `detail`)
SELECT 'ROOM_ITEM_DUP', 'pet_room_item', MIN(i.`id`), NULL,
       JSON_OBJECT('roomId', i.`room_id`, 'itemCode', i.`item_code`,
                   'ids', JSON_ARRAYAGG(i.`id`), 'action', 'KEEP_EARLIEST_OTHERS_ARCHIVED')
FROM `pet_room_item` i
WHERE i.`archived_at` IS NULL
GROUP BY i.`room_id`, i.`item_code`
HAVING COUNT(*) > 1;

UPDATE `pet_room_item` a
JOIN (
    SELECT ranked.id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY room_id, item_code ORDER BY id ASC) AS rn
        FROM `pet_room_item`
        WHERE `archived_at` IS NULL
    ) ranked
    WHERE ranked.rn > 1
) dup ON a.`id` = dup.`id`
SET a.`archived_at` = UTC_TIMESTAMP();
