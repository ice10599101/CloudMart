-- F3：好友动态 Feed——收件箱模式：事件发生时向 actor 的全部好友扇出写一行
-- （好友量 ≤100 可接受扇出写），收件人游标分页拉取 + 已读水位。

CREATE TABLE IF NOT EXISTS `pet_friend_feed` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法,兼游标)',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '收件人用户ID(好友)',
    `actor_user_id` BIGINT UNSIGNED NOT NULL COMMENT '动态主体用户ID',
    `actor_pet_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '动态主体宠物ID',
    `event_type`    VARCHAR(32) NOT NULL COMMENT '事件类型: LEVEL_UP/WORK_COMPLETED/STUDY_COMPLETED/BATTLE_WIN',
    `payload_json`  VARCHAR(500) NOT NULL COMMENT '展示载荷: {"text":"...","petName":"..."}',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_friend_feed` (`id`),
    INDEX `idx_friend_feed_user_id` (`user_id`, `id`),
    INDEX `idx_friend_feed_actor` (`actor_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友动态收件箱(扇出写,游标分页)';

CREATE TABLE IF NOT EXISTS `pet_friend_feed_cursor` (
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `last_read_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已读水位(最后读到的 feed id)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_friend_feed_cursor` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友动态已读水位';
