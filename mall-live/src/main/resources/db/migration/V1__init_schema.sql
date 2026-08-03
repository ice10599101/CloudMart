-- CloudMart Live 直播模块数据库初始化
CREATE TABLE IF NOT EXISTS `live_rooms` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title` VARCHAR(256) NOT NULL COMMENT '直播间标题',
    `description` TEXT COMMENT '直播间描述',
    `anchor_user_id` BIGINT UNSIGNED NOT NULL COMMENT '主播用户ID',
    `anchor_name` VARCHAR(64) NOT NULL COMMENT '主播昵称',
    `cover_image` VARCHAR(512) COMMENT '封面图URL',
    `stream_url` VARCHAR(512) COMMENT '直播流地址',
    `product_id` BIGINT UNSIGNED COMMENT '关联商品ID（专属秒杀）',
    `seckill_activity_id` BIGINT UNSIGNED COMMENT '关联秒杀活动ID',
    `max_viewers` INT NOT NULL DEFAULT 0 COMMENT '最大在线人数，0=不限',
    `current_viewers` INT NOT NULL DEFAULT 0 COMMENT '当前在线人数',
    `total_viewers` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计观看人数',
    `status` VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT '状态: OFFLINE/LIVE/ENDED',
    `start_time` DATETIME COMMENT '开播时间',
    `end_time` DATETIME COMMENT '结束时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_live_rooms` (`id`),
    INDEX `idx_live_rooms_anchor` (`anchor_user_id`),
    INDEX `idx_live_rooms_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='直播间表';

CREATE TABLE IF NOT EXISTS `live_danmaku` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_id` BIGINT UNSIGNED NOT NULL COMMENT '直播间ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '发送用户ID',
    `nickname` VARCHAR(64) NOT NULL COMMENT '用户昵称',
    `content` VARCHAR(500) NOT NULL COMMENT '弹幕内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY `pk_live_danmaku` (`id`),
    INDEX `idx_live_danmaku_room` (`room_id`),
    INDEX `idx_live_danmaku_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='弹幕记录表';
