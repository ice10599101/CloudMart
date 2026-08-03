-- CloudMart mall-notification 初始 Schema 基线

CREATE TABLE IF NOT EXISTS `notifications` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `type` varchar(30) NOT NULL COMMENT '通知类型: SECKILL_RESULT-秒杀结果, ORDER_STATUS-订单状态, SYSTEM-系统通知',
  `title` varchar(200) NOT NULL COMMENT '通知标题',
  `content` varchar(1000) NOT NULL COMMENT '通知内容',
  `is_read` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否已读: 0-未读, 1-已读',
  `biz_id` bigint unsigned DEFAULT NULL COMMENT '关联业务ID',
  `biz_type` varchar(30) DEFAULT NULL COMMENT '关联业务类型: ORDER-订单, SECKILL-秒杀',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_read` (`user_id`,`is_read`),
  KEY `idx_user_id_created` (`user_id`,`created_at`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知消息表';
