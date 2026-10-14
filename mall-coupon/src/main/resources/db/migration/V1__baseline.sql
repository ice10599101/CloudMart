-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `coupon_claim_counter` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '计数ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `template_id` bigint unsigned NOT NULL COMMENT '券模板ID',
  `claimed_count` int NOT NULL DEFAULT '0' COMMENT '已领取数量',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_claim_counter_user_template` (`user_id`,`template_id`),
  CONSTRAINT `chk_claim_count` CHECK ((`claimed_count` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='领券计数台账';

CREATE TABLE `coupon_templates` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `name` varchar(100) NOT NULL COMMENT '优惠券名称',
  `type` varchar(20) NOT NULL COMMENT '类型: AMOUNT_OFF-满减, PERCENT_OFF-折扣',
  `threshold_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '使用门槛金额',
  `discount_amount` decimal(10,2) DEFAULT NULL COMMENT '优惠金额(满减券)',
  `discount_rate` decimal(3,2) DEFAULT NULL COMMENT '折扣率(折扣券, 0.10~0.99)',
  `total_quantity` int unsigned NOT NULL COMMENT '总发行量',
  `remaining_quantity` int unsigned NOT NULL COMMENT '剩余库存',
  `per_user_limit` int unsigned NOT NULL DEFAULT '1' COMMENT '每人限领数量',
  `validity_type` varchar(20) NOT NULL COMMENT '有效期类型: FIXED_DATE-固定时间段, FIXED_DAYS-领取后固定天数',
  `start_time` datetime DEFAULT NULL COMMENT '固定有效期开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '固定有效期结束时间',
  `valid_days` int unsigned DEFAULT NULL COMMENT '领取后有效天数',
  `status` varchar(20) NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED-启用, DISABLED-禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券模板表';

CREATE TABLE `exchange_codes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(32) NOT NULL COMMENT '兑换码(Base32编码, 去除易混淆字符)',
  `template_id` bigint unsigned NOT NULL COMMENT '关联的优惠券模板ID',
  `serial_number` int unsigned NOT NULL COMMENT '序列号(用于BitMap位定位)',
  `status` varchar(20) NOT NULL DEFAULT 'UNUSED' COMMENT '状态: UNUSED-未兑换, EXCHANGED-已兑换, DISABLED-已作废',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '兑换用户ID',
  `exchange_batch` varchar(64) DEFAULT NULL COMMENT '生成批次号(用于批量管理)',
  `exchanged_at` datetime DEFAULT NULL COMMENT '兑换时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_template_id` (`template_id`),
  KEY `idx_template_serial` (`template_id`,`serial_number`),
  KEY `idx_status` (`status`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_exchange_batch` (`exchange_batch`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券兑换码表';

CREATE TABLE `undo_log` (
  `branch_id` bigint NOT NULL COMMENT '分支事务ID',
  `xid` varchar(128) NOT NULL COMMENT '全局事务ID',
  `context` varchar(128) NOT NULL COMMENT '上下文',
  `rollback_info` longblob NOT NULL COMMENT '回滚信息',
  `log_status` int NOT NULL COMMENT '日志状态',
  `log_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `log_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`branch_id`),
  KEY `idx_xid` (`xid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Seata AT 模式 undo_log';

CREATE TABLE `user_coupons` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户券ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `template_id` bigint unsigned NOT NULL COMMENT '优惠券模板ID',
  `status` varchar(20) NOT NULL DEFAULT 'UNUSED' COMMENT '状态: UNUSED-未使用, USED-已使用, EXPIRED-已过期',
  `order_id` bigint unsigned DEFAULT NULL COMMENT '核销时关联的订单ID',
  `received_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `used_at` datetime DEFAULT NULL COMMENT '使用时间',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_status` (`user_id`,`status`),
  KEY `idx_template_id` (`template_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户优惠券表';
