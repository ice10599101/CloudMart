CREATE TABLE IF NOT EXISTS `risk_records` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `action_type` VARCHAR(50) NOT NULL COMMENT '操作类型',
    `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级: LOW/MEDIUM/HIGH',
    `result` VARCHAR(20) NOT NULL COMMENT '处理结果: PASS/BLOCK/REVIEW',
    `rule_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '触发规则ID',
    `detail` VARCHAR(500) DEFAULT NULL COMMENT '详情',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_risk_records` (`id`),
    INDEX `idx_risk_records_user` (`user_id`),
    INDEX `idx_risk_records_action` (`action_type`),
    INDEX `idx_risk_records_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控记录表';

CREATE TABLE IF NOT EXISTS `risk_rules` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(100) NOT NULL COMMENT '规则名称',
    `action_type` VARCHAR(50) NOT NULL COMMENT '监控操作类型',
    `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级: LOW/MEDIUM/HIGH',
    `threshold` INT NOT NULL DEFAULT 1 COMMENT '触发阈值',
    `time_window_minutes` INT NOT NULL DEFAULT 60 COMMENT '时间窗口(分钟)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '规则描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_risk_rules` (`id`),
    INDEX `idx_risk_rules_action` (`action_type`),
    INDEX `idx_risk_rules_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控规则表';
