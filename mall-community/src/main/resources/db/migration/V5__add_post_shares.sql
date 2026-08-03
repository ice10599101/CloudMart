CREATE TABLE IF NOT EXISTS `post_shares` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `post_id` bigint unsigned NOT NULL COMMENT '帖子ID',
  `user_id` bigint unsigned NOT NULL COMMENT '分享用户ID',
  `channel` varchar(20) NOT NULL DEFAULT 'LINK' COMMENT '分享渠道: LINK/WECHAT/WEIBO/QQ/DOUBAN',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_channel` (`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子分享记录表';
