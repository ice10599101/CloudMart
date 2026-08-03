-- CloudMart mall-job 初始 Schema 基线
-- Quartz 表由 Spring Boot Quartz 自动创建，此处仅管理业务表

CREATE TABLE IF NOT EXISTS `sys_job` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) NOT NULL DEFAULT '' COMMENT '调用目标字符串',
  `cron_expression` varchar(255) NOT NULL DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` tinyint NOT NULL DEFAULT '1' COMMENT '计划执行策略（1立即 2放弃 3下次）',
  `concurrent` tinyint NOT NULL DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务调度表';

CREATE TABLE IF NOT EXISTS `sys_job_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `job_name` varchar(64) NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) NOT NULL DEFAULT '' COMMENT '调用目标字符串',
  `job_message` varchar(500) DEFAULT '' COMMENT '日志信息',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '执行状态（0成功 1失败）',
  `exception_info` varchar(2000) DEFAULT '' COMMENT '异常信息',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_job_log_name` (`job_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务调度日志表';
