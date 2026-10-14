-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `compensation_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` varchar(128) NOT NULL COMMENT '业务幂等键（如 stock-confirm:{orderId}:{skuId}）',
  `action` varchar(64) NOT NULL COMMENT '补偿动作标识',
  `aggregate_id` varchar(64) NOT NULL COMMENT '聚合根ID（如订单号）',
  `payload` text COMMENT '任务载荷JSON',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/SUCCEEDED/FAILED/DEAD_LETTER',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间（退避）',
  `last_error` varchar(1024) DEFAULT NULL COMMENT '最后错误（脱敏）',
  `locked_by` varchar(64) DEFAULT NULL COMMENT '执行者实例ID（租约）',
  `locked_at` datetime(3) DEFAULT NULL COMMENT '执行锁时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compensation_task_id` (`task_id`),
  KEY `idx_compensation_status_retry` (`status`,`next_retry_at`),
  KEY `idx_compensation_aggregate` (`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨服务补偿任务（ASYNC-01）';

CREATE TABLE `inbox_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `consumer` varchar(64) NOT NULL COMMENT '消费者标识',
  `event_id` varchar(64) NOT NULL COMMENT '全局唯一事件ID',
  `event_type` varchar(128) NOT NULL COMMENT '事件类型',
  `aggregate_id` varchar(64) NOT NULL COMMENT '聚合根ID',
  `status` varchar(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/PROCESSED/FAILED',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '处理尝试次数',
  `last_error` varchar(1024) DEFAULT NULL COMMENT '最后错误（脱敏）',
  `locked_at` datetime(3) DEFAULT NULL COMMENT '处理锁时间（租约）',
  `processed_at` datetime(3) DEFAULT NULL COMMENT '处理完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbox_consumer_event` (`consumer`,`event_id`),
  KEY `idx_inbox_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事件消费Inbox（ASYNC-01）';

CREATE TABLE `inventory` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '库存记录ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `available` int unsigned NOT NULL DEFAULT '0' COMMENT '可用库存',
  `reserved` int unsigned NOT NULL DEFAULT '0' COMMENT '预占库存',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_id` (`sku_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存表';

CREATE TABLE `inventory_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `type` varchar(20) NOT NULL COMMENT '操作类型: DEDUCT/RELEASE/CONFIRM',
  `quantity` int unsigned NOT NULL COMMENT '操作数量',
  `order_id` bigint unsigned DEFAULT NULL COMMENT '关联订单ID',
  `receipt_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '来源收货流水ID（T19：RESTOCK 入账幂等键）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_logs_receipt` (`receipt_id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存操作日志';

CREATE TABLE `inventory_reservation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '预占ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID（0=历史无单预占，仅排空期兼容）',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `quantity` int NOT NULL COMMENT '预占数量（正数）',
  `status` varchar(20) NOT NULL DEFAULT 'RESERVED' COMMENT '状态：RESERVED/CONFIRMED/RELEASED',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观版本（状态每次迁移 +1）',
  `confirmed_at` datetime(3) DEFAULT NULL COMMENT '确认时间',
  `released_at` datetime(3) DEFAULT NULL COMMENT '释放时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_order_sku` (`order_id`,`sku_id`),
  KEY `idx_reservation_status` (`status`,`updated_at`),
  KEY `idx_reservation_sku` (`sku_id`,`status`),
  CONSTRAINT `chk_reservation_quantity` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存预占台账';

CREATE TABLE `outbox_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` varchar(64) NOT NULL COMMENT '全局唯一事件ID（UUID）',
  `event_type` varchar(128) NOT NULL COMMENT '事件类型（如 PAYMENT_SUCCESS）',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT '载荷结构版本',
  `aggregate_id` varchar(64) NOT NULL COMMENT '聚合根ID（如订单号）',
  `aggregate_version` bigint NOT NULL DEFAULT '0' COMMENT '聚合版本（乱序去重）',
  `request_id` varchar(64) DEFAULT NULL COMMENT '发起请求追踪ID',
  `payload` text NOT NULL COMMENT '事件载荷JSON',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SENDING/SENT/FAILED/DEAD_LETTER',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间（退避）',
  `last_error` varchar(1024) DEFAULT NULL COMMENT '最后错误（脱敏）',
  `locked_by` varchar(64) DEFAULT NULL COMMENT '投递者实例ID（租约）',
  `locked_at` datetime(3) DEFAULT NULL COMMENT '投递锁时间',
  `lease_version` int NOT NULL DEFAULT '0' COMMENT '租约版本（T16 fencing：每次认领+1，回写校验）',
  `sent_at` datetime(3) DEFAULT NULL COMMENT '投递成功时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_status_retry` (`status`,`next_retry_at`),
  KEY `idx_outbox_aggregate` (`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易事件Outbox（ASYNC-01）';

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
