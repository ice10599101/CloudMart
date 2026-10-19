-- UX05：“我的软件”回访入口——更新已读台账。
-- 阅读状态只影响未读展示，不影响通知投递事实（投递行仍由 SF05 任务独立管理）。
-- event_id 对应 software_subscription_delivery.id（一次定向投递 = 一个更新事件）。

CREATE TABLE `software_update_read` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `delivery_id` bigint unsigned NOT NULL COMMENT '更新事件（software_subscription_delivery.id）',
  `read_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '已读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_update_read` (`user_id`,`delivery_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅更新已读台账（UX05：重复标记幂等；不复制发布内容）';
