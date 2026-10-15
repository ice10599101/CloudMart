-- =============================================================================
-- V7__software_p1.sql — P1 增强（方案 4.5/6.11/9.4）
-- 评分 / 订阅 / 更新提醒偏好 / 专题与排序项 / 订阅投递与 fanout 任务。
-- 说明：V6 已用于审计约束修复，P1 从 V7 起编（15.1 原计划编号顺延）。
-- software_comment.pinned 在 V2 已建，无需变更。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 评分（4.5）：每用户每软件一条，1~5 星可修改/撤销；均分由
-- software.rating_count / rating_sum 同事务维护（零票显示"暂无评分"）。
-- -----------------------------------------------------------------------------
CREATE TABLE `software_rating` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `user_id` bigint unsigned NOT NULL COMMENT '评分人（分享者本人不可评）',
  `score` tinyint unsigned NOT NULL COMMENT '1~5 星',
  `lock_version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rating_user_software` (`user_id`,`software_id`),
  KEY `idx_rating_software` (`software_id`,`user_id`),
  CONSTRAINT `chk_rating_score` CHECK (`score` BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件评分（P1）';

-- -----------------------------------------------------------------------------
-- 订阅（4.5）：与收藏分开；仅接收新增版本通知；无 updated_at；
-- fanout 按 (software_id,id) 游标分批扫描，禁止一次读取全部订阅。
-- -----------------------------------------------------------------------------
CREATE TABLE `software_subscription` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键（fanout 游标键）',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `user_id` bigint unsigned NOT NULL COMMENT '订阅人',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '订阅时间（发布时的候选判定依据）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subscription_user_software` (`user_id`,`software_id`),
  KEY `idx_subscription_software` (`software_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件更新订阅（P1）';

-- -----------------------------------------------------------------------------
-- 更新提醒偏好（4.5）：软件更新提醒总开关（默认开启，支持关闭）
-- -----------------------------------------------------------------------------
CREATE TABLE `software_user_preference` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `update_notifications` tinyint(1) NOT NULL DEFAULT '1' COMMENT '软件更新提醒开关',
  `lock_version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_preference_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件更新提醒偏好（P1）';

-- -----------------------------------------------------------------------------
-- 专题（6.11）：公开查询只显示 PUBLISHED，且不可见软件从清单实时过滤
-- -----------------------------------------------------------------------------
CREATE TABLE `software_topic` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `slug` varchar(64) NOT NULL COMMENT '专题标识（URL 用）',
  `title` varchar(80) NOT NULL COMMENT '标题',
  `summary` varchar(500) DEFAULT NULL COMMENT '运营说明',
  `cover_asset_id` bigint unsigned DEFAULT NULL COMMENT '封面素材（mall-file 资产ID，逻辑引用）',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/DISABLED',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_slug` (`slug`),
  KEY `idx_topic_status` (`status`,`sort_order`,`id`),
  CONSTRAINT `chk_topic_status` CHECK (`status` IN ('DRAFT','PUBLISHED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件专题合集（P1）';

CREATE TABLE `software_topic_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `topic_id` bigint unsigned NOT NULL COMMENT '专题ID',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID（公开查询按可见性实时过滤）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '专题内排序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_item` (`topic_id`,`software_id`),
  KEY `idx_topic_item_order` (`topic_id`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专题软件清单（P1）';

-- -----------------------------------------------------------------------------
-- 订阅投递（9.4）：唯一键 (software_id,publication_seq,user_id)；
-- 失败按指数退避重试，超限转 FAILED（人工重试保留同一通知去重键）。
-- -----------------------------------------------------------------------------
CREATE TABLE `software_subscription_delivery` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `publication_seq` int unsigned NOT NULL COMMENT '上线序号（本次发布）',
  `user_id` bigint unsigned NOT NULL COMMENT '订阅人',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/CANCELLED/FAILED',
  `attempts` int unsigned NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间',
  `lease_owner` varchar(64) DEFAULT NULL COMMENT '领取者（fencing）',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '租约到期',
  `lease_version` int unsigned NOT NULL DEFAULT '0' COMMENT '租约版本（fencing）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery` (`software_id`,`publication_seq`,`user_id`),
  KEY `idx_delivery_due` (`status`,`next_retry_at`,`id`),
  CONSTRAINT `chk_delivery_status`
    CHECK (`status` IN ('PENDING','SENT','CANCELLED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅更新投递（P1 fanout）';

-- -----------------------------------------------------------------------------
-- fanout 任务（9.4）：软件发布事件的游标任务；批次插入 delivery 与推进
-- cursor_subscription_id 同事务；领取带租约版本 fencing，宕机可续跑。
-- -----------------------------------------------------------------------------
CREATE TABLE `software_fanout_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `revision_id` bigint unsigned NOT NULL COMMENT '本次发布快照',
  `publication_seq` int unsigned NOT NULL COMMENT '上线序号',
  `published_at` datetime(3) NOT NULL COMMENT '发布时间（候选订阅判定）',
  `cursor_subscription_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '扫描游标（subscription.id）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
  `lease_owner` varchar(64) DEFAULT NULL COMMENT '领取者（fencing）',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '租约到期',
  `lease_version` int unsigned NOT NULL DEFAULT '0' COMMENT '租约版本（fencing）',
  `attempts` int unsigned NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近失败原因（后台可见）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fanout` (`software_id`,`publication_seq`),
  KEY `idx_fanout_due` (`status`,`next_retry_at`,`id`),
  CONSTRAINT `chk_fanout_status` CHECK (`status` IN ('PENDING','RUNNING','DONE','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅 fanout 任务（P1）';
