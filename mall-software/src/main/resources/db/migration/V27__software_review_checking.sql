-- =============================================================================
-- V27__software_review_checking.sql — SW04：提交先冻结（CHECKING）再异步检查
--
-- 1) 审核状态新增 CHECKING：提交事务内冻结内容并落检查任务，检查在事务外执行；
--    自动通过 CHECKING→PENDING（等待人工），自动拒绝 CHECKING→REJECTED 并生成新 DRAFT。
-- 2) 检查任务持久化：租约 + fencing + 退避重试；临时失败保持 CHECKING，
--    超过重试上限进入 DEAD_LETTER，由后台运维人工重试，超时不伪装成通过。
-- =============================================================================

ALTER TABLE `software_revision`
  DROP CHECK `chk_revision_status`,
  ADD CONSTRAINT `chk_revision_status`
    CHECK (`review_status` IN ('DRAFT','CHECKING','PENDING','WITHDRAWN','APPROVED','REJECTED'));

CREATE TABLE `software_review_check_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `revision_id` bigint unsigned NOT NULL COMMENT '不可变提交记录ID（结果只能作用于该记录）',
  `operation_key` varchar(128) NOT NULL COMMENT '提交幂等键（远程检查幂等身份）',
  `content_digest` char(64) NOT NULL COMMENT '冻结内容摘要（检查前复核必须一致）',
  `auto_publish` tinyint(1) NOT NULL DEFAULT '0' COMMENT '提交时具有直接发布权限：检查通过且无需人工复核时自动上线',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING/RUNNING/DONE/REJECTED/DEAD_LETTER/CANCELLED',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间（指数退避）',
  `locked_by` varchar(64) DEFAULT NULL COMMENT '执行者实例ID（租约）',
  `locked_at` datetime(3) DEFAULT NULL COMMENT '领取时间',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '租约到期',
  `lease_version` int NOT NULL DEFAULT '0' COMMENT '租约版本（fencing：回写必须匹配）',
  `last_error` varchar(1024) DEFAULT NULL COMMENT '最后错误（脱敏）',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '终态时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_check_revision` (`revision_id`),
  KEY `idx_review_check_status_retry` (`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提交自动审核检查任务（冻结后异步执行，租约+fencing+退避）';
