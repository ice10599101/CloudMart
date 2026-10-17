-- WISH-03 全链：品牌许愿池账务与运营闭环
-- 1) 奖励义务唯一事实：(pool_id,user_id,reward_type) 唯一 + 结算状态机字段；
-- 2) 池发布冻结规则版本；3) 品牌审计日志表（审批/池变更留痕）。
ALTER TABLE `wish_brand_reward_log`
    ADD COLUMN `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '结算状态: PENDING/CREDITED/FAILED(WISH-03)' AFTER `reward_amount`,
    ADD COLUMN `promised_amount` int NOT NULL DEFAULT '0' COMMENT '承诺数量(WISH-03)' AFTER `status`,
    ADD COLUMN `credited_amount` int NOT NULL DEFAULT '0' COMMENT '实际入账数量(可少于承诺:每日上限, WISH-03)' AFTER `promised_amount`,
    ADD COLUMN `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数(WISH-03)' AFTER `credited_amount`,
    ADD COLUMN `last_error` varchar(500) DEFAULT NULL COMMENT '最近错误(WISH-03)' AFTER `retry_count`,
    ADD COLUMN `operation_id` varchar(64) DEFAULT NULL COMMENT '幂等入账操作键(WISH-03)' AFTER `last_error`,
    ADD UNIQUE KEY `uk_pool_user_type` (`pool_id`, `user_id`, `reward_type`);

ALTER TABLE `wish_brand_pool`
    ADD COLUMN `rule_version` int NOT NULL DEFAULT '1' COMMENT '发布时冻结的规则版本(WISH-03)' AFTER `reward_json`;

CREATE TABLE `wish_brand_audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `brand_id` bigint unsigned DEFAULT NULL COMMENT '品牌 ID',
  `pool_id` bigint unsigned DEFAULT NULL COMMENT '许愿池 ID',
  `admin_user_id` bigint unsigned NOT NULL COMMENT '操作管理员 ID',
  `action` varchar(32) NOT NULL COMMENT '动作: APPROVE/REJECT/POOL_CREATE/POOL_UPDATE/POOL_PUBLISH/POOL_END/SETTLE_RETRY',
  `detail` varchar(1000) DEFAULT NULL COMMENT '详情(变更摘要/原因)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_audit_brand` (`brand_id`, `created_at`),
  KEY `idx_audit_pool` (`pool_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌与许愿池审计日志(WISH-03)';
