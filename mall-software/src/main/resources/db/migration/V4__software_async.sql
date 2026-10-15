-- =============================================================================
-- V4__software_async.sql — 通用异步可靠性三表（Outbox/Inbox/补偿，方案 6.12/9.4）
--                          + 软件域注销任务（11.3）
-- 三表结构复刻 mall-common/async 现行 Mapper 字段（含租约与 fencing 字段），
-- 建在 mall_software；禁止跨库读写其他服务的异步表。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 事务性事件发件箱（ASYNC-01）：同本地事务写入，租约调度投递 software-events
-- -----------------------------------------------------------------------------
CREATE TABLE `outbox_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` varchar(64) NOT NULL COMMENT '全局唯一事件ID（UUID，消费者按 eventId+recipientId 去重）',
  `event_type` varchar(128) NOT NULL COMMENT '事件类型（SOFTWARE_REVIEW_APPROVED 等）',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT '载荷结构版本',
  `aggregate_id` varchar(64) NOT NULL COMMENT '聚合根ID（软件ID）',
  `aggregate_version` bigint NOT NULL DEFAULT '0' COMMENT '聚合版本（乱序去重）',
  `request_id` varchar(64) DEFAULT NULL COMMENT '发起请求追踪ID',
  `payload` text NOT NULL COMMENT '事件载荷JSON（不含 URL/提取码/密码/正文）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING/SENDING/SENT/FAILED/DEAD_LETTER',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间（退避）',
  `last_error` varchar(1024) DEFAULT NULL COMMENT '最后错误（脱敏）',
  `locked_by` varchar(64) DEFAULT NULL COMMENT '投递者实例ID（租约）',
  `locked_at` datetime(3) DEFAULT NULL COMMENT '投递锁时间',
  `lease_version` int NOT NULL DEFAULT '0' COMMENT '租约版本（fencing：每次认领+1，回写校验）',
  `sent_at` datetime(3) DEFAULT NULL COMMENT '投递成功时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_status_retry` (`status`,`next_retry_at`),
  KEY `idx_outbox_aggregate` (`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件事件Outbox（同事务写入，租约+fencing 可靠投递）';

-- -----------------------------------------------------------------------------
-- 事件消费收件箱（ASYNC-01）：软件域自身消费（如有）按 consumer+eventId 幂等
-- -----------------------------------------------------------------------------
CREATE TABLE `inbox_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `consumer` varchar(64) NOT NULL COMMENT '消费者标识',
  `event_id` varchar(64) NOT NULL COMMENT '全局唯一事件ID',
  `event_type` varchar(128) NOT NULL COMMENT '事件类型',
  `aggregate_id` varchar(64) NOT NULL COMMENT '聚合根ID',
  `status` varchar(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态:PROCESSING/PROCESSED/FAILED',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '处理尝试次数',
  `last_error` varchar(1024) DEFAULT NULL COMMENT '最后错误（脱敏）',
  `locked_at` datetime(3) DEFAULT NULL COMMENT '处理锁时间（租约）',
  `processed_at` datetime(3) DEFAULT NULL COMMENT '处理完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbox_consumer_event` (`consumer`,`event_id`),
  KEY `idx_inbox_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件域事件消费Inbox（幂等消费）';

-- -----------------------------------------------------------------------------
-- 跨服务补偿任务（ASYNC-01）：素材引用解绑等跨服务补偿持久化重试
-- -----------------------------------------------------------------------------
CREATE TABLE `compensation_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` varchar(128) NOT NULL COMMENT '业务幂等键（如 software-ref-unbind:{revisionId}:{assetId}）',
  `action` varchar(64) NOT NULL COMMENT '补偿动作标识',
  `aggregate_id` varchar(64) NOT NULL COMMENT '聚合根ID',
  `payload` text COMMENT '任务载荷JSON',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING/PROCESSING/SUCCEEDED/FAILED/DEAD_LETTER',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间（退避）',
  `last_error` varchar(1024) DEFAULT NULL COMMENT '最后错误（脱敏）',
  `locked_by` varchar(64) DEFAULT NULL COMMENT '执行者实例ID（租约）',
  `locked_at` datetime(3) DEFAULT NULL COMMENT '执行锁时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compensation_task_id` (`task_id`),
  KEY `idx_compensation_status_retry` (`status`,`next_retry_at`),
  KEY `idx_compensation_aggregate` (`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件域跨服务补偿任务（素材引用解绑等）';

-- -----------------------------------------------------------------------------
-- 软件域注销任务（11.3）：mall-user 注销编排 SOFTWARE/ERASE 步骤的可续跑持久化状态
-- -----------------------------------------------------------------------------
CREATE TABLE `software_erasure_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '注销用户（前台会员ID）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING/RUNNING/DONE/FAILED（DONE 后重复调用返回成功）',
  `phase` varchar(40) NOT NULL DEFAULT '' COMMENT '当前阶段（下架软件→匿名化评论→清理关系→解绑引用等）',
  `cursor_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '阶段内游标（大批量分批处理，宕机续跑）',
  `attempts` int unsigned NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间（退避）',
  `lease_owner` varchar(64) DEFAULT NULL COMMENT '执行者实例ID（租约）',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '租约到期',
  `lease_version` int unsigned NOT NULL DEFAULT '0' COMMENT '租约版本（fencing）',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最后错误（脱敏）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_erasure_user` (`user_id`),
  KEY `idx_erasure_status_retry` (`status`,`next_retry_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件域账号注销任务（每阶段游标与清理同事务，未完成不得推进文件擦除）';
