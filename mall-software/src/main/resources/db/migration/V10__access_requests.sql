-- =============================================================================
-- V10__access_requests.sql — SF04 受限来源访问申请（人工授权闭环）
-- 每用户每软件最多一条 PENDING：生成列只在 PENDING 时给出槽位值，三元组唯一。
-- 审批通过时回写 grant_id 关联既有授权事实；撤销授权不删除申请历史。
-- =============================================================================

CREATE TABLE `software_access_request` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `applicant_id` bigint unsigned NOT NULL COMMENT '申请人（前台会员ID）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED/CANCELLED',
  `message` varchar(1000) NOT NULL COMMENT '申请说明（用途/凭据线索）',
  `evidence_asset_ids` json DEFAULT NULL COMMENT '佐证素材（本人已上传资产ID数组，仅授权员可见）',
  `external_reference` varchar(200) DEFAULT NULL COMMENT '人工核对凭据（如线下单号，不伪造已支付）',
  `grant_id` bigint unsigned DEFAULT NULL COMMENT '批准后关联的授权记录ID',
  `handled_by` bigint unsigned DEFAULT NULL COMMENT '处理管理员ID',
  `handled_at` datetime(3) DEFAULT NULL COMMENT '处理时间',
  `reason` varchar(500) DEFAULT NULL COMMENT '通过说明/拒绝可公开原因',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `pending_slot` varchar(1) GENERATED ALWAYS AS (CASE WHEN `status` = 'PENDING' THEN 'P' ELSE NULL END) STORED COMMENT '未结申请槽位（每用户每软件仅一条 PENDING）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_access_request_pending` (`software_id`, `applicant_id`, `pending_slot`),
  KEY `idx_access_request_status` (`status`, `created_at`),
  KEY `idx_access_request_applicant` (`applicant_id`),
  CONSTRAINT `chk_access_request_status` CHECK (`status` IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受限来源访问申请（SF04：申请→人工核对→授权/拒绝，闭环可解释）';
