-- =============================================================================
-- V2__broadcast_job.sql — ADMIN-03：通知群发批次任务化
-- 群发不再是"向全员循环插入"的瞬时动作：以批次任务落库，返回 jobId 与
-- 目标/成功/失败/跳过计数；重复提交按 request_id 幂等返回原批次；
-- 明细按 (job,user) 唯一，失败项可安全重试（记录 notification_id 防重复投递）。
-- =============================================================================

CREATE TABLE `notification_broadcast_job` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `job_no` varchar(32) NOT NULL COMMENT '批次号（BRD 前缀），展示与重试主键',
  `request_id` varchar(64) NOT NULL COMMENT '提交幂等键（客户端生成，重复提交返回原批次）',
  `type` varchar(30) NOT NULL COMMENT '通知类型',
  `title` varchar(100) NOT NULL COMMENT '通知标题',
  `content` text NOT NULL COMMENT '通知内容',
  `target_count` int NOT NULL DEFAULT 0 COMMENT '目标用户数',
  `delivered_count` int NOT NULL DEFAULT 0 COMMENT '已投递数',
  `failed_count` int NOT NULL DEFAULT 0 COMMENT '失败数（可重试）',
  `skipped_count` int NOT NULL DEFAULT 0 COMMENT '跳过数（禁用账号等）',
  `status` varchar(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'RUNNING/COMPLETED/PARTIAL/FAILED',
  `created_by` bigint unsigned DEFAULT NULL COMMENT '操作管理员（X-User-Id）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_broadcast_job_no` (`job_no`),
  UNIQUE KEY `uk_broadcast_request` (`request_id`),
  KEY `idx_broadcast_job_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知群发批次任务';

CREATE TABLE `notification_broadcast_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint unsigned NOT NULL COMMENT '批次 ID',
  `user_id` bigint unsigned NOT NULL COMMENT '目标用户 ID',
  `status` varchar(20) NOT NULL COMMENT 'DELIVERED/FAILED/SKIPPED',
  `notification_id` bigint unsigned DEFAULT NULL COMMENT '投递成功后的通知 ID（重试幂等依据）',
  `error` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_broadcast_item` (`job_id`,`user_id`),
  KEY `idx_broadcast_item_retry` (`job_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知群发明细';
