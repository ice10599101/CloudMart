-- =============================================================================
-- V15__sf06_daily_stat.sql — SF06 可解释统计
-- 1) software_stat_event：收藏/订阅增减事实事件（取消后关系行消失，必须有事件
--    才能分别记录新增与取消）；resolve 类口径沿用 software_link_access_daily
--    （唯一事实源，禁止另建）。
-- 2) software_daily_stat：日聚合表（可由 rebuild 全量精确重算；唯一键保证
--    重复汇总不累计翻倍）。时间一律 Asia/Shanghai 自然日（与 access_daily 一致）。
-- =============================================================================

CREATE TABLE `software_stat_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `user_id` bigint unsigned NOT NULL COMMENT '动作人（作者本人动作不计入对外指标口径）',
  `kind` varchar(24) NOT NULL COMMENT '事件:FAVORITE_ADD/FAVORITE_REMOVE/SUBSCRIBE_ADD/SUBSCRIBE_REMOVE',
  `event_date` date NOT NULL COMMENT '事件日期（Asia/Shanghai 切日）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_stat_event` (`event_date`,`software_id`,`kind`),
  KEY `idx_stat_event_software` (`software_id`,`event_date`),
  CONSTRAINT `chk_stat_event_kind` CHECK (`kind` IN
      ('FAVORITE_ADD','FAVORITE_REMOVE','SUBSCRIBE_ADD','SUBSCRIBE_REMOVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='收藏/订阅增减事实事件（SF06；resolve 事实源为 software_link_access_daily）';

CREATE TABLE `software_daily_stat` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `stat_date` date NOT NULL COMMENT '统计日期（Asia/Shanghai 自然日）',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `resolve_count` int unsigned NOT NULL DEFAULT 0
      COMMENT '获取链接次数（access_daily 行数口径；去重获取，非外部下载量）',
  `resolve_user_count` int unsigned NOT NULL DEFAULT 0
      COMMENT '获取链接人数（access_daily 当日去重用户）',
  `favorite_add` int unsigned NOT NULL DEFAULT 0 COMMENT '新增收藏数（事件）',
  `favorite_remove` int unsigned NOT NULL DEFAULT 0 COMMENT '取消收藏数（事件）',
  `subscribe_add` int unsigned NOT NULL DEFAULT 0 COMMENT '新增订阅数（事件）',
  `subscribe_remove` int unsigned NOT NULL DEFAULT 0 COMMENT '取消订阅数（事件）',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_stat` (`stat_date`,`software_id`),
  KEY `idx_daily_stat_range` (`stat_date`,`software_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='软件日聚合（SF06；rebuild 全量重算，重复执行不翻倍）';
