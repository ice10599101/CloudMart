-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V35
-- 模块: mall-wish
-- 说明: 漂流瓶支持实名/匿名投瓶 + 瓶下评论树（默认匿名，可切换实名）
--       wish_drift_bottle 增加 is_anonymous 标记；
--       新建 wish_drift_bottle_comment 评论表（仅投瓶人与捞起人可见/可评）。
-- =============================================

ALTER TABLE `wish_drift_bottle`
    ADD COLUMN `is_anonymous` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '投瓶是否匿名(1匿名隐藏身份/0实名,捞起者可见投瓶人身份)' AFTER `content`;

-- ---------------------------------------------
-- wish_drift_bottle_comment 漂流瓶评论
-- 评论者只能为投瓶人或捞起人；is_anonymous=1 时对外隐藏身份（显示匿名瓶友）；
-- parent_id 指向被回复的评论（顶级评论为 NULL），reply_to_user_id 为被回复人（服务端从父评论推导）。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_drift_bottle_comment` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `bottle_id`        BIGINT UNSIGNED NOT NULL COMMENT '漂流瓶ID',
    `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '评论者ID(投瓶人或捞起人)',
    `parent_id`        BIGINT UNSIGNED DEFAULT NULL COMMENT '父评论ID(回复场景,顶级评论为NULL)',
    `reply_to_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '被回复人ID(顶级评论为NULL)',
    `content`          VARCHAR(500) NOT NULL COMMENT '评论内容(纯文本,最长500字)',
    `is_anonymous`     TINYINT(1) NOT NULL DEFAULT 1 COMMENT '评论是否匿名(1匿名/0实名)',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_wish_drift_bottle_comment` (`id`),
    INDEX `idx_drift_comment_bottle` (`bottle_id`, `id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='漂流瓶评论(默认匿名,可切换实名)';