-- =============================================================================
-- V3__software_permissions.sql — 发布资格、付款查看授权、联系入口、
--     权限审计、软件审核日志、操作幂等事实（方案 6.10/6.14）
-- 权限审计独立于 software_audit_log（发布资格针对用户，可能尚无 softwareId）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 发布资格（4.0/6.14）：默认关闭；记录缺失与 false 均禁止发布；仅管理员逐用户开通
-- -----------------------------------------------------------------------------
CREATE TABLE `software_publisher_permission` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '前台会员ID（mall-user，不是后台管理员ID；唯一键幂等首开）',
  `can_publish` tinyint(1) NOT NULL DEFAULT '0' COMMENT '发布资格开关（数据库为最终事实；缓存仅用于UI提示）',
  `updated_by` bigint unsigned NOT NULL COMMENT '最近操作管理员ID',
  `reason` varchar(500) NOT NULL COMMENT '开通/关闭原因（审计要求）',
  `lock_version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本（独立 CAS，不参与发布协议）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_publisher_user` (`user_id`),
  KEY `idx_publisher_can_publish` (`can_publish`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发布资格（默认关闭，管理员逐用户开通；投稿/审核锁协议起点）';

-- -----------------------------------------------------------------------------
-- 付款查看授权（5.4/6.14）：一会员+一款软件；管理员线下确认后手动开通
-- -----------------------------------------------------------------------------
CREATE TABLE `software_access_grant` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '授权软件',
  `user_id` bigint unsigned NOT NULL COMMENT '授权会员（前台ID）',
  `status` varchar(16) NOT NULL COMMENT '状态:ACTIVE/REVOKED（过期由服务端当前时间实时判定）',
  `granted_by` bigint unsigned NOT NULL COMMENT '开通管理员ID',
  `granted_at` datetime(3) NOT NULL COMMENT '开通时间',
  `expires_at` datetime(3) DEFAULT NULL COMMENT '到期时间（NULL=长期有效；恰好到期即无效）',
  `revoked_by` bigint unsigned DEFAULT NULL COMMENT '撤销管理员ID',
  `revoked_at` datetime(3) DEFAULT NULL COMMENT '撤销时间',
  `confirmation_note` varchar(500) NOT NULL COMMENT '管理员人工确认备注（线下付款核对；仅有权管理员可见）',
  `external_reference` varchar(128) DEFAULT NULL COMMENT '外部核对编号（线下凭证引用，非支付回调）',
  `lock_version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_grant_software_user` (`software_id`,`user_id`),
  KEY `idx_grant_user_status` (`user_id`,`status`,`software_id`),
  CONSTRAINT `chk_grant_status` CHECK (`status` IN ('ACTIVE','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款查看授权（管理员人工开通，覆盖当前及后续版本全部来源）';

-- -----------------------------------------------------------------------------
-- 管理员联系入口（5.4/6.14）：后台统一配置；发布者只能选择已启用入口
-- -----------------------------------------------------------------------------
CREATE TABLE `software_access_contact` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(80) NOT NULL COMMENT '入口名称',
  `contact_type` varchar(24) NOT NULL COMMENT '类型:SITE_USER/TEXT/HTTPS_URL',
  `contact_value` varchar(500) NOT NULL COMMENT '联系内容（管理员维护；拒绝秘密信息和危险协议）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '启用（停用后新提交付款模式失败；已有授权不受影响）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `updated_by` bigint unsigned NOT NULL COMMENT '最近操作管理员ID',
  `lock_version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_contact_type` CHECK (`contact_type` IN ('SITE_USER','TEXT','HTTPS_URL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款查看联系入口（引用中仅允许停用，不物理删除）';

-- -----------------------------------------------------------------------------
-- 权限审计（6.14）：发布资格/授权/联系入口操作历史；只追加，不设 updated_at
-- -----------------------------------------------------------------------------
CREATE TABLE `software_permission_audit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `target_user_id` bigint unsigned DEFAULT NULL COMMENT '目标会员（发布资格/授权操作时）',
  `software_id` bigint unsigned DEFAULT NULL COMMENT '关联软件（授权操作时；发布资格可无）',
  `contact_id` bigint unsigned DEFAULT NULL COMMENT '关联联系入口（联系入口操作时）',
  `actor_admin_id` bigint unsigned NOT NULL COMMENT '操作管理员ID',
  `action` varchar(40) NOT NULL COMMENT '动作:PUBLISH_ENABLE/PUBLISH_DISABLE/ACCESS_GRANT/ACCESS_REVOKE/ACCESS_EXTEND/CONTACT_UPDATE',
  `before_json` json DEFAULT NULL COMMENT '变更前状态快照（不含下载链接/支付敏感信息）',
  `after_json` json NOT NULL COMMENT '变更后状态快照（不含下载链接/支付敏感信息）',
  `reason` varchar(500) NOT NULL COMMENT '操作原因',
  `request_id` varchar(128) DEFAULT NULL COMMENT '请求追踪ID（Idempotency-Key 关联）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_perm_audit_user` (`target_user_id`,`created_at`,`id`),
  KEY `idx_perm_audit_software` (`software_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限变更审计（发布资格/付款授权/联系入口，只追加）';

-- -----------------------------------------------------------------------------
-- 软件审核日志（6.10）：投稿/审核/状态流转历史；只追加，不覆盖历史原因
-- -----------------------------------------------------------------------------
CREATE TABLE `software_audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `revision_id` bigint unsigned DEFAULT NULL COMMENT '关联快照（可空：lifecycle 类操作）',
  `actor_id` bigint unsigned NOT NULL COMMENT '操作者ID（USER=会员/ADMIN=管理员/SYSTEM=系统）',
  `actor_type` varchar(16) NOT NULL COMMENT '操作者类型:USER/ADMIN/SYSTEM',
  `action` varchar(40) NOT NULL COMMENT '动作（CREATE/SUBMIT/WITHDRAW/APPROVE/REJECT/UNLIST/RELIST/BAN/UNBAN/DELETE 等）',
  `from_status` varchar(20) DEFAULT NULL COMMENT '流转前状态',
  `to_status` varchar(20) DEFAULT NULL COMMENT '流转后状态',
  `reason` varchar(1000) DEFAULT NULL COMMENT '操作原因（只追加，不覆盖）',
  `request_id` varchar(128) DEFAULT NULL COMMENT '请求追踪ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_log_software` (`software_id`,`created_at`,`id`),
  KEY `idx_audit_log_revision` (`revision_id`),
  CONSTRAINT `chk_audit_log_actor` CHECK (`actor_type` IN ('USER','ADMIN','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件域审核与状态流转日志（不记录下载URL/密码）';

-- -----------------------------------------------------------------------------
-- 操作幂等事实（6.10/7.3）：创建软件/克隆工作快照/发布提交/审核切换/创建评论等；
-- 业务变更与结果记录同事务提交；同键同请求返回原结果，同键异体 409
-- -----------------------------------------------------------------------------
CREATE TABLE `software_operation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `actor_type` varchar(16) NOT NULL COMMENT '操作者类型:USER/ADMIN（区分用户和管理员 ID 命名空间）',
  `actor_id` bigint unsigned NOT NULL COMMENT '操作者ID',
  `operation_key` varchar(128) NOT NULL COMMENT '操作幂等键（客户端 Idempotency-Key）',
  `operation_type` varchar(40) NOT NULL COMMENT '操作类型（区分创建软件/创建评论/审核切换等，避免同 key 撞用）',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求体规范化摘要（同键异体 409 判定）',
  `software_id` bigint unsigned DEFAULT NULL COMMENT '关联软件（可空操作）',
  `result_json` json NOT NULL COMMENT '首次执行结果（只存 ID/版本号/状态，不存源链接）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次执行时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_key` (`actor_type`,`actor_id`,`operation_type`,`operation_key`),
  CONSTRAINT `chk_operation_actor` CHECK (`actor_type` IN ('USER','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作幂等事实（P0 至少保留 30 天后清理）';
