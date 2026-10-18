-- =============================================================================
-- V3__file_deletion_recovery.sql — T27：文件删除失败可恢复 + 孤儿实物回收义务
--
-- 1) file_asset 增加删除恢复字段：删除卡在 DELETING 时记录失败原因/尝试次数/下次重试
--    时间，恢复扫描据此重试物理删除并最终落 DELETED；README 里不再靠"再点一次"碰运气。
-- 2) file_orphan_reclaim 登记"上传失败清理实物失败"的孤儿对象回收义务（白名单回收，
--    只删本表登记的 storageKey，不接受任意路径）。
-- =============================================================================

ALTER TABLE `file_asset`
  ADD COLUMN `delete_error` varchar(512) DEFAULT NULL COMMENT '删除失败原因（DELETING 卡住时）' AFTER `status`,
  ADD COLUMN `delete_attempts` int unsigned NOT NULL DEFAULT 0 COMMENT '删除重试次数（恢复扫描用）' AFTER `delete_error`,
  ADD COLUMN `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间（UTC，指数退避）' AFTER `delete_attempts`,
  ADD KEY `idx_file_asset_deleting_retry` (`status`, `next_retry_at`);

CREATE TABLE `file_orphan_reclaim` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '回收义务ID',
  `storage_key` varchar(512) NOT NULL COMMENT '孤儿实物存储键（白名单内才可回收）',
  `reason` varchar(64) NOT NULL COMMENT '产生原因（如 UPLOAD_CLEANUP_FAILED）',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RECLAIMED/FAILED',
  `attempts` int unsigned NOT NULL DEFAULT 0 COMMENT '回收尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间（UTC，指数退避）',
  `error` varchar(512) DEFAULT NULL COMMENT '最近一次回收失败原因',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登记时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orphan_storage_key` (`storage_key`),
  KEY `idx_orphan_reclaim_retry` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件孤儿实物回收义务台账';
