-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `live_danmaku` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `room_id` bigint unsigned NOT NULL COMMENT '直播间ID',
  `user_id` bigint unsigned NOT NULL COMMENT '发送用户ID',
  `nickname` varchar(64) NOT NULL COMMENT '用户昵称',
  `content` varchar(500) NOT NULL COMMENT '弹幕内容',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_live_danmaku_room` (`room_id`),
  KEY `idx_live_danmaku_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='弹幕记录表';

CREATE TABLE `live_rooms` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(256) NOT NULL COMMENT '直播间标题',
  `description` text COMMENT '直播间描述',
  `anchor_user_id` bigint unsigned NOT NULL COMMENT '主播用户ID',
  `anchor_name` varchar(64) NOT NULL COMMENT '主播昵称',
  `cover_image` varchar(512) DEFAULT NULL COMMENT '封面图URL',
  `stream_url` varchar(512) DEFAULT NULL COMMENT '直播流地址',
  `product_id` bigint unsigned DEFAULT NULL COMMENT '关联商品ID（专属秒杀）',
  `seckill_activity_id` bigint unsigned DEFAULT NULL COMMENT '关联秒杀活动ID',
  `max_viewers` int NOT NULL DEFAULT '0' COMMENT '最大在线人数，0=不限',
  `current_viewers` int NOT NULL DEFAULT '0' COMMENT '当前在线人数',
  `total_viewers` bigint unsigned NOT NULL DEFAULT '0' COMMENT '累计观看人数',
  `status` varchar(20) NOT NULL DEFAULT 'OFFLINE' COMMENT '状态: OFFLINE/LIVE/ENDED',
  `start_time` datetime DEFAULT NULL COMMENT '开播时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_live_rooms_anchor` (`anchor_user_id`),
  KEY `idx_live_rooms_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='直播间表';
