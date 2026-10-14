-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `shipping_addresses` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货人手机号',
  `province` varchar(32) NOT NULL COMMENT '省',
  `city` varchar(32) NOT NULL COMMENT '市',
  `district` varchar(32) NOT NULL COMMENT '区',
  `detail_address` varchar(256) NOT NULL COMMENT '详细地址',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认: 0-否, 1-是',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址表';

CREATE TABLE `user_account_deletion_step` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint unsigned NOT NULL COMMENT '注销任务ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `domain` varchar(32) NOT NULL COMMENT '域:USER/AUTH/WISH/ORDER/COMMUNITY/NOTIFICATION/FILE/PET',
  `step` varchar(64) NOT NULL COMMENT '步骤:SESSION_REVOKE/OPEN_ORDER_CHECK/ERASE/ANONYMIZE',
  `status` enum('PENDING','RUNNING','SUCCESS','FAILED','BLOCKED') NOT NULL DEFAULT 'PENDING' COMMENT '步骤状态:PENDING待执行/RUNNING执行中/SUCCESS成功/FAILED失败待重试/BLOCKED硬阻断',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次可重试时间(退避)',
  `lease_owner` varchar(64) DEFAULT NULL COMMENT '租约持有者(实例标识)',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '租约到期',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近错误(脱敏)',
  `completed_at` datetime(3) DEFAULT NULL COMMENT '完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_deletion_step` (`task_id`,`domain`,`step`),
  KEY `idx_deletion_step_status` (`status`,`next_retry_at`),
  KEY `idx_deletion_step_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='注销步骤台账(T06,分域可恢复)';

CREATE TABLE `user_account_deletion_task` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `status` enum('PENDING','PRECHECK','EXECUTING','COMPLETED','BLOCKED','CANCELED','EXECUTED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态:PENDING等待期/PRECHECK预检/EXECUTING执行中/COMPLETED全域完成/BLOCKED阻断可重试/CANCELED已撤销/EXECUTED旧语义(需补核查)',
  `reason` varchar(500) DEFAULT NULL COMMENT '注销原因',
  `service_progress` json DEFAULT NULL COMMENT '各服务清理进度(wish=SUCCESS/FAILED)',
  `block_reason` varchar(200) DEFAULT NULL COMMENT '阻断原因(OPEN_ORDERS/ERASURE_DOMAIN_NOT_WIRED等,脱敏展示)',
  `requested_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
  `execute_after` datetime(3) NOT NULL COMMENT '宽限期截止(30天)',
  `executed_at` datetime(3) DEFAULT NULL COMMENT '执行完成时间',
  `canceled_at` datetime(3) DEFAULT NULL COMMENT '取消时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_deletion_task_user` (`user_id`),
  KEY `idx_deletion_task_status` (`status`,`execute_after`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全账号注销编排任务(B20)';

CREATE TABLE `user_invite_code` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `code` varchar(12) NOT NULL COMMENT '邀请码（8 位大写字符数字）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请码（N-3）';

CREATE TABLE `user_invite_relation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `inviter_id` bigint unsigned NOT NULL COMMENT '邀请人用户 ID',
  `invitee_id` bigint unsigned NOT NULL COMMENT '受邀人用户 ID（注册后绑定）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invitee` (`invitee_id`),
  KEY `idx_inviter` (`inviter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请绑定关系（N-3）';

CREATE TABLE `user_reward_obligation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `biz_key` varchar(96) NOT NULL COMMENT '业务幂等键（INVITE_RELATION:{id}:{role}，uk）',
  `source` varchar(24) NOT NULL COMMENT 'INVITE_REWARD',
  `source_biz_id` varchar(64) NOT NULL COMMENT '来源事实 ID（邀请关系 ID）',
  `beneficiary` bigint unsigned NOT NULL COMMENT '受益人用户 ID',
  `reward_type` varchar(24) NOT NULL COMMENT 'STARLIGHT',
  `amount` int NOT NULL COMMENT '数量',
  `description` varchar(200) DEFAULT NULL COMMENT '备注',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED/REJECTED',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `last_error` varchar(300) DEFAULT NULL COMMENT '最近错误',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间（退避）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `settled_at` datetime DEFAULT NULL COMMENT '交付完成时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_key` (`biz_key`),
  KEY `idx_status_retry` (`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请奖励交付义务（R07）';

CREATE TABLE `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '小答号',
  `password` varchar(100) NOT NULL COMMENT '密码(BCrypt)',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `nickname_updated_at` datetime DEFAULT NULL COMMENT '昵称上次修改时间',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `signature` varchar(200) DEFAULT NULL COMMENT '个性签名',
  `gender` varchar(10) DEFAULT NULL COMMENT '性别',
  `birthday` varchar(20) DEFAULT NULL COMMENT '生日',
  `constellation` varchar(20) DEFAULT NULL COMMENT '星座',
  `occupation` varchar(50) DEFAULT NULL COMMENT '职业',
  `school` varchar(100) DEFAULT NULL COMMENT '学校',
  `location` varchar(100) DEFAULT NULL COMMENT '所在地区',
  `hobbies` varchar(200) DEFAULT NULL COMMENT '兴趣爱好',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-正常',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_users_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
