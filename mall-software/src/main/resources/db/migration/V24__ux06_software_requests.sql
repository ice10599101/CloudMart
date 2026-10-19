-- UX06：找软件需求（SOFTWARE_DISCOVERY）——软件域独立轻量表。
-- 取舍记录：优先评估复用 mall-wish，但其提交链路与奖励/成长/AI 深度耦合
-- （提交默认发放奖励与成长值），违反"不默认扣款或发奖励"硬约束；
-- 故按"复用优先、约束不满足时独立建表"原则（同 UX04 guide）建软件域轻表，
-- 不建奖励/徽章/成长联动。审核复用软件域内容审核流程；状态显式映射测试。

CREATE TABLE `software_request` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '发起人（会员 ID）',
  `title` varchar(80) NOT NULL COMMENT '一句话需求（≤80 字）',
  `usage_desc` varchar(500) NOT NULL COMMENT '用途描述：要完成什么、什么场景',
  `must_have_features` varchar(300) DEFAULT NULL COMMENT '必须功能（逗号分隔 ≤300 字）',
  `platform_scope` varchar(200) DEFAULT NULL COMMENT '可接受平台 CSV（WINDOWS/MACOS/LINUX/WEB）',
  `acceptable_license` varchar(100) DEFAULT NULL COMMENT '可接受许可（FREE/OPENSOURCE/COMMERCIAL 等 CSV）',
  `source_keyword` varchar(100) DEFAULT NULL COMMENT '来源搜索关键词（从搜索无结果进入时保留，便于回跳）',
  `status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT '前台状态:OPEN(寻找中)/ANSWERED(已找到)/CLOSED(已关闭)',
  `audit_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核:PENDING/APPROVED/REJECTED',
  `audit_reason` varchar(200) DEFAULT NULL COMMENT '审核意见',
  `matched_software_id` bigint unsigned DEFAULT NULL COMMENT '已找到软件（必须为可见软件；下架后标记可解释）',
  `matched_reason` varchar(200) DEFAULT NULL COMMENT '已找到理由（运营或发起人填写）',
  `matched_at` datetime(3) DEFAULT NULL COMMENT '标记时间',
  `matched_by` bigint unsigned DEFAULT NULL COMMENT '标记人（管理员或发起人）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '删除时间（软删除）',
  PRIMARY KEY (`id`),
  KEY `idx_request_public` (`audit_status`,`status`,`created_at`),
  KEY `idx_request_user` (`user_id`,`created_at`),
  CONSTRAINT `chk_request_status` CHECK (`status` IN ('OPEN','ANSWERED','CLOSED')),
  CONSTRAINT `chk_request_audit` CHECK (`audit_status` IN ('PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件需求（UX06：SOFTWARE_DISCOVERY；无奖励系统；审核+已找到标记）';
