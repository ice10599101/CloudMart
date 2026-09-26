-- V8: B02 长期活动互斥——每用户至多一条进行中活动（覆盖普通工作/职业工作/读书/捞瓶/休息/托管）
-- 步骤（§7.1 顺序）：
--   1. 建冲突预检报告表 pet_migration_conflict（只读清单，审计依据，不做静默删除）；
--   2. 将"实际已到期"的多余 IN_PROGRESS 先结算为 COMPLETED（定时任务遗漏的历史欠账）；
--   3. 剩余未到期冲突：保留最早开始的一条为 IN_PROGRESS，其余置 EXPIRED 并在 result 里
--      写迁移标记（保留事实行 + 审计，不清除记录；受影响用户清单落报告表）；
--   4. 清零冲突后替换唯一索引为"每用户一条 IN_PROGRESS"。

CREATE TABLE IF NOT EXISTS `pet_migration_conflict` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(自增)',
    `conflict_type` VARCHAR(60) NOT NULL COMMENT '冲突类型:ACTIVITY_MULTI_ACTIVE/BATTLE_SEED_RANGE/RELATION_OVERFLOW/ROOM_ITEM_DUP/EVENT_SNAPSHOT_MISSING',
    `entity_table`  VARCHAR(60) NOT NULL COMMENT '实体表名',
    `entity_id`     BIGINT UNSIGNED NOT NULL COMMENT '实体ID',
    `user_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '归属用户ID',
    `detail`        JSON DEFAULT NULL COMMENT '冲突明细(相关行/处理动作/原因)',
    `resolved`      TINYINT NOT NULL DEFAULT 0 COMMENT '是否已处理:0待人工核对/1已按修复方案处理',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间(UTC)',
    PRIMARY KEY `pk_pet_migration_conflict` (`id`),
    INDEX `idx_pet_migration_conflict_type` (`conflict_type`, `resolved`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据迁移冲突预检报告(只读审计,不静默删数据)';

-- 1) 记录全部多活动冲突用户（IN_PROGRESS 多于一行的用户）
INSERT INTO `pet_migration_conflict` (`conflict_type`, `entity_table`, `entity_id`, `user_id`, `detail`)
SELECT 'ACTIVITY_MULTI_ACTIVE', 'pet_activity', MIN(a.id), a.user_id,
       JSON_OBJECT('inProgressCount', COUNT(*), 'activityIds', JSON_ARRAYAGG(a.id),
                   'types', JSON_ARRAYAGG(a.activity_type),
                   'action', 'KEEP_EARLIEST_OTHERS_EXPIRED_OR_SETTLED')
FROM `pet_activity` a
WHERE a.status = 'IN_PROGRESS'
GROUP BY a.user_id
HAVING COUNT(*) > 1;

-- 2) 实际已到期的 IN_PROGRESS 全部结算为 COMPLETED（等待领取，不丢失奖励事实）
UPDATE `pet_activity`
SET `status` = 'COMPLETED'
WHERE `status` = 'IN_PROGRESS' AND `finished_at` <= UTC_TIMESTAMP();

-- 3) 剩余未到期冲突：每用户保留最早开始的一条（started_at,id 序），其余置 EXPIRED 并写迁移标记
UPDATE `pet_activity` a
JOIN (
    SELECT ranked.id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY started_at ASC, id ASC) AS rn
        FROM `pet_activity`
        WHERE `status` = 'IN_PROGRESS'
    ) ranked
    WHERE ranked.rn > 1
) dup ON a.`id` = dup.`id`
SET a.`status` = 'EXPIRED',
    a.`result` = JSON_OBJECT('migratedFrom', 'IN_PROGRESS', 'reason', 'B02_UNIQUE_ACTIVE_MIGRATION');

-- 4) 清零验证 + 替换唯一索引：同用户同类型改为同用户任意类型
--    （若上方 UPDATE 后仍存在未到期冲突——理论上不可能——索引创建会失败并阻断发布，符合"必须清零"要求）
ALTER TABLE `pet_activity` DROP INDEX `uk_activity_user_active`;
ALTER TABLE `pet_activity`
    ADD UNIQUE KEY `uk_activity_user_active_v2` (`user_id`, (IF(`status` = 'IN_PROGRESS', 1, NULL)));
