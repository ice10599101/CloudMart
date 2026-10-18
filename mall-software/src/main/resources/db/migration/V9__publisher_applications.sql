-- =============================================================================
-- V9__publisher_applications.sql — SF03 发布者申请（用户申请 → 后台审核闭环）
-- 每用户最多一条未结申请：生成列只在 PENDING 时给出槽位值，与 user_id 组成唯一键；
-- 非 PENDING（APPROVED/REJECTED/CANCELLED）行不影响再次申请。
-- =============================================================================

CREATE TABLE `software_publisher_application` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '申请人（前台会员ID）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED/CANCELLED',
  `intro` varchar(1000) NOT NULL COMMENT '发布用途与来源说明（审核依据）',
  `intended_categories` varchar(500) NOT NULL COMMENT '拟投分类（逗号分隔的展示文本）',
  `evidence_asset_ids` json DEFAULT NULL COMMENT '佐证素材（本人已上传资产ID数组，可空）',
  `submitted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
  `reviewed_by` bigint unsigned DEFAULT NULL COMMENT '审核管理员ID',
  `reviewed_at` datetime(3) DEFAULT NULL COMMENT '审核时间',
  `reason` varchar(500) DEFAULT NULL COMMENT '通过说明/拒绝可公开原因',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `pending_slot` varchar(1) GENERATED ALWAYS AS (CASE WHEN `status` = 'PENDING' THEN 'P' ELSE NULL END) STORED COMMENT '未结申请槽位（每用户仅一条 PENDING）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_publisher_application_pending` (`user_id`, `pending_slot`),
  KEY `idx_publisher_application_status` (`status`, `submitted_at`),
  CONSTRAINT `chk_publisher_application_status` CHECK (`status` IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发布者申请（SF03：申请→审核→授予/拒绝，闭环可解释）';
