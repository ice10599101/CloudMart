-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `blacklist_entries` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `target_type` varchar(20) NOT NULL COMMENT '目标类型: USER/IP/DEVICE',
  `target_value` varchar(100) NOT NULL COMMENT '目标值: 用户ID/IP地址/设备ID',
  `reason` varchar(500) DEFAULT NULL COMMENT '加入黑名单原因',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间，NULL表示永久',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_blacklist_target` (`target_type`,`target_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='黑名单表';

CREATE TABLE `risk_records` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `action_type` varchar(50) NOT NULL COMMENT '操作类型',
  `risk_level` varchar(20) NOT NULL COMMENT '风险等级: LOW/MEDIUM/HIGH',
  `result` varchar(20) NOT NULL COMMENT '处理结果: PASS/BLOCK/REVIEW',
  `rule_id` bigint unsigned DEFAULT NULL COMMENT '触发规则ID',
  `detail` varchar(500) DEFAULT NULL COMMENT '详情',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_risk_records_user` (`user_id`),
  KEY `idx_risk_records_action` (`action_type`),
  KEY `idx_risk_records_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控记录表';

CREATE TABLE `risk_rules` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '规则名称',
  `action_type` varchar(50) NOT NULL COMMENT '监控操作类型',
  `risk_level` varchar(20) NOT NULL COMMENT '风险等级: LOW/MEDIUM/HIGH',
  `threshold` int NOT NULL DEFAULT '1' COMMENT '触发阈值',
  `time_window_minutes` int NOT NULL DEFAULT '60' COMMENT '时间窗口(分钟)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
  `priority` int NOT NULL DEFAULT '0' COMMENT '评估优先级（小者先评）',
  `description` varchar(500) DEFAULT NULL COMMENT '规则描述',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_risk_rules_action` (`action_type`),
  KEY `idx_risk_rules_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控规则表';
