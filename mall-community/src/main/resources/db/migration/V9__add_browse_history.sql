CREATE TABLE IF NOT EXISTS `browse_histories` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint unsigned NOT NULL COMMENT '浏览用户ID',
  `target_type` varchar(20) NOT NULL COMMENT '浏览对象类型：PRODUCT-商品 POST-帖子 WISH-心愿',
  `target_id` bigint unsigned NOT NULL COMMENT '浏览对象ID',
  `title` varchar(200) NOT NULL DEFAULT '' COMMENT '浏览对象标题快照',
  `cover` varchar(500) NOT NULL DEFAULT '' COMMENT '浏览对象封面快照',
  `viewed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近浏览时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次浏览时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_user_viewed` (`user_id`, `viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浏览足迹表';