-- =============================================================================
-- V28__sf08_source_appeals.sql — SF08 补批：作者来源申诉
--
-- 1) 作者可对当前已发布内容中被处置为 INVALID/DISABLED/SUSPECTED 的来源申诉，
--    记录关联处置 ID 与处置快照（可用性/原因/时间），保留历史。
-- 2) 同一来源同时只允许一条 PENDING 申诉（生成列 + 唯一键），重复提交返回冲突；
--    处理结论 ACCEPTED/REJECTED 只落一次，重试幂等。
-- 3) ACCEPTED 仅把来源恢复为 UNKNOWN 待重检（不直接判 AVAILABLE），
--    最终可用性仍由巡检/人工核实决定。
-- =============================================================================

CREATE TABLE `software_source_appeal` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `source_key` varchar(128) NOT NULL COMMENT '稳定来源键',
  `disposition_id` bigint unsigned DEFAULT NULL COMMENT '关联处置ID（software_source_state.id）',
  `disposition_availability` varchar(16) DEFAULT NULL COMMENT '申诉时的处置结论快照',
  `disposition_reason` varchar(500) DEFAULT NULL COMMENT '申诉时的处置原因快照',
  `disposition_at` datetime(3) DEFAULT NULL COMMENT '申诉时的处置时间快照',
  `appellant_id` bigint unsigned NOT NULL COMMENT '申诉人（软件作者）',
  `reason` varchar(1000) NOT NULL COMMENT '申诉理由',
  `evidence` varchar(2000) DEFAULT NULL COMMENT '证据说明（纯文本，不含受限链接/凭据）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING/ACCEPTED/REJECTED',
  `handler_id` bigint unsigned DEFAULT NULL COMMENT '处理管理员ID',
  `handle_note` varchar(500) DEFAULT NULL COMMENT '处理说明',
  `handled_at` datetime(3) DEFAULT NULL COMMENT '处理时间',
  `pending_guard` tinyint GENERATED ALWAYS AS (IF(`status` = 'PENDING', 1, NULL)) STORED
    COMMENT 'PENDING 唯一闸门（非 PENDING 为 NULL，可保留多条历史）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_appeal_pending` (`software_id`, `source_key`, `pending_guard`),
  KEY `idx_source_appeal_software` (`software_id`, `id`),
  KEY `idx_source_appeal_status` (`status`, `id`),
  KEY `idx_source_appeal_appellant` (`appellant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作者来源申诉（关联处置快照，处理结论幂等）';
