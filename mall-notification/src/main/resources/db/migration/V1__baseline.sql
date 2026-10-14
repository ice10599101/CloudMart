-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `conversations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user1_id` bigint unsigned NOT NULL COMMENT '用户1 ID(较小ID)',
  `user2_id` bigint unsigned NOT NULL COMMENT '用户2 ID(较大ID)',
  `last_message` varchar(500) DEFAULT NULL COMMENT '最后一条消息内容',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后消息时间',
  `user1_unread_count` int unsigned NOT NULL DEFAULT '0' COMMENT '用户1未读消息数',
  `user2_unread_count` int unsigned NOT NULL DEFAULT '0' COMMENT '用户2未读消息数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_pair` (`user1_id`,`user2_id`),
  KEY `idx_user1_id` (`user1_id`),
  KEY `idx_user2_id` (`user2_id`),
  KEY `idx_last_message_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私信会话表';

CREATE TABLE `messages` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` bigint unsigned NOT NULL COMMENT '会话ID',
  `sender_id` bigint unsigned NOT NULL COMMENT '发送者ID',
  `content` varchar(2000) NOT NULL COMMENT '消息内容',
  `type` varchar(20) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型: TEXT-文本, IMAGE-图片, PRODUCT-商品卡片',
  `client_message_id` varchar(64) DEFAULT NULL COMMENT '客户端消息幂等键(N01:同会话+发送者唯一,重发不重复)',
  `is_recalled` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否撤回: 0-否, 1-是',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_client` (`conversation_id`,`sender_id`,`client_message_id`),
  KEY `idx_conversation_id_created` (`conversation_id`,`created_at`),
  KEY `idx_sender_id` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私信消息表';

CREATE TABLE `notifications` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `type` varchar(30) NOT NULL COMMENT '通知类型: SECKILL_RESULT-秒杀结果, ORDER_STATUS-订单状态, SYSTEM-系统通知',
  `title` varchar(200) NOT NULL COMMENT '通知标题',
  `content` text NOT NULL COMMENT '通知内容（公告推送为富文本 HTML，扩容为 TEXT）',
  `is_read` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否已读: 0-未读, 1-已读',
  `biz_id` bigint unsigned DEFAULT NULL COMMENT '关联业务ID',
  `biz_type` varchar(30) DEFAULT NULL COMMENT '关联业务类型: ORDER-订单, SECKILL-秒杀',
  `event_id` varchar(120) DEFAULT NULL COMMENT '业务事件唯一键(宠物outbox事件TYPE:实例,消费去重)',
  `actor_id` bigint unsigned DEFAULT NULL COMMENT '操作者用户ID（谁做的互动），NULL=系统/历史通知',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_event` (`event_id`),
  KEY `idx_user_id_read` (`user_id`,`is_read`),
  KEY `idx_user_id_created` (`user_id`,`created_at`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知消息表';

CREATE TABLE `subscribe_message_binding` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `openid` varchar(64) NOT NULL COMMENT '用户小程序 openid（code2session）',
  `template_key` varchar(32) NOT NULL COMMENT '业务模板键（ANNIVERSARY 等）',
  `consumed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '额度是否已消耗',
  `consumed_at` datetime DEFAULT NULL COMMENT '消耗时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_key_consumed` (`user_id`,`template_key`,`consumed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅消息授权额度（N-1）';
