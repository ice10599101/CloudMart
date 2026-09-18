-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V36
-- 模块: mall-wish
-- 说明: 漂流瓶重设计
--       1) 状态机扩展: FLOATING(漂流中) → PICKED(已被捞起) → RETURNED(被扔回海里,回到海面可再被捞起);
--          「被回复/被收藏」为展示层状态（依据评论数/收藏标记推导），不入库
--       2) 捞瓶人匿名开关 picker_is_anonymous（投瓶人侧可见性）+ 收藏标记 is_collected
--       3) return_count 记录回流次数；is_hidden 管理端下架（软隐藏，保留数据）
--       4) 新增 wish_drift_bottle_fish_log 打捞流水：每日打捞配额(20次/天)与看板趋势统计，
--          扔回海里不清除流水（历史事实）
-- =============================================

ALTER TABLE `wish_drift_bottle`
    MODIFY COLUMN `status` ENUM('FLOATING','PICKED','RETURNED') NOT NULL DEFAULT 'FLOATING'
        COMMENT '状态:漂流中/已被捞起/被扔回海里(扔回后回到海面可再被捞起)',
    ADD COLUMN `picker_is_anonymous` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '捞瓶人是否匿名(1匿名/0实名,实名时投瓶人可见捞瓶人身份)' AFTER `is_anonymous`,
    ADD COLUMN `is_collected` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '捞起人是否已收藏该瓶(扔回海里时重置)' AFTER `picker_is_anonymous`,
    ADD COLUMN `return_count` INT NOT NULL DEFAULT 0
        COMMENT '被扔回海里次数' AFTER `is_collected`,
    ADD COLUMN `is_hidden` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '管理端下架(1用户端不可见,数据保留)' AFTER `return_count`,
    ADD INDEX `idx_drift_bottle_throw_time` (`thrower_user_id`, `thrown_at`);

-- ---------------------------------------------
-- wish_drift_bottle_fish_log 打捞流水
-- 每次成功捞起插入一条；扔回海里不删除（配额与趋势以流水为准）。
-- 扔回后的捞起历史保留，便于看板统计与配额防刷。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_drift_bottle_fish_log` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `bottle_id`  BIGINT UNSIGNED NOT NULL COMMENT '漂流瓶ID',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '打捞人用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打捞时间(UTC)',
    PRIMARY KEY `pk_wish_drift_bottle_fish_log` (`id`),
    INDEX `idx_drift_fish_user` (`user_id`, `created_at`),
    INDEX `idx_drift_fish_bottle` (`bottle_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='漂流瓶打捞流水(每日配额/看板趋势)';
