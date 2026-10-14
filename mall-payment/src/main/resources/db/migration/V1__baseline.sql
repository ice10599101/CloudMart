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

CREATE TABLE `payment_attempt` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '尝试ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `merchant_payment_no` varchar(64) NOT NULL COMMENT '商户支付号（对渠道稳定，唯一）',
  `channel` varchar(32) NOT NULL COMMENT '渠道：MOCK/ALIPAY/WECHAT',
  `scene` varchar(16) NOT NULL DEFAULT 'H5' COMMENT 'R09 支付场景：H5/MINIAPP/APP',
  `request_key` varchar(64) DEFAULT NULL COMMENT 'R09 创建尝试幂等键（同键稳定返回同一笔尝试）',
  `active_order_id` bigint unsigned GENERATED ALWAYS AS (if((`status` = _utf8mb4'PENDING'),`order_id`,NULL)) STORED COMMENT 'R09 活动尝试约束列：仅 PENDING 时=order_id',
  `amount` decimal(18,2) NOT NULL COMMENT '应付金额（服务端计算）',
  `currency` char(3) NOT NULL DEFAULT 'CNY' COMMENT '币种（首期 CNY）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/CLOSED/FAILED',
  `provider_txn_no` varchar(64) DEFAULT NULL COMMENT '渠道交易号',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观版本',
  `expires_at` datetime(3) DEFAULT NULL COMMENT '尝试有效期',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_merchant_no` (`merchant_payment_no`),
  UNIQUE KEY `uk_attempt_request_key` (`request_key`),
  UNIQUE KEY `uk_attempt_active_order` (`active_order_id`),
  KEY `idx_attempt_order` (`order_id`,`status`),
  CONSTRAINT `chk_attempt_amount` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付尝试台账（PAY-01）';

CREATE TABLE `payment_notify_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `channel` varchar(32) NOT NULL COMMENT '渠道',
  `notification_id` varchar(128) NOT NULL COMMENT '渠道通知唯一标识（防重放幂等键）',
  `signature_valid` tinyint(1) NOT NULL COMMENT '验签结果',
  `handle_result` varchar(16) NOT NULL COMMENT '处理结果：SUCCESS/DUPLICATE/REJECTED/FAILED',
  `payment_attempt_id` bigint unsigned DEFAULT NULL COMMENT '关联尝试ID',
  `detail` varchar(500) DEFAULT NULL COMMENT '摘要（脱敏，不含敏感原文）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '接收时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_channel_id` (`channel`,`notification_id`),
  KEY `idx_notify_attempt` (`payment_attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道通知日志（PAY-01）';

CREATE TABLE `reconciliation_difference` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '差异ID',
  `run_id` bigint unsigned NOT NULL COMMENT '对账运行ID',
  `diff_type` varchar(64) NOT NULL COMMENT '差异类型（如 PAYMENT_SUCCESS_ORDER_NOT_PAID）',
  `biz_id` varchar(64) NOT NULL COMMENT '业务主键（如 paymentId/orderId）',
  `severity` varchar(16) NOT NULL DEFAULT 'HIGH' COMMENT 'HIGH/MEDIUM/LOW',
  `detail` varchar(1000) DEFAULT NULL COMMENT '差异详情（脱敏）',
  `evidence` varchar(1000) DEFAULT NULL COMMENT '证据（两侧状态快照 JSON）',
  `resolve_status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/ACCEPTED',
  `resolved_by` bigint unsigned DEFAULT NULL COMMENT '处置管理员',
  `resolve_note` varchar(500) DEFAULT NULL COMMENT '处置说明',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发现时间',
  `resolved_at` datetime(3) DEFAULT NULL COMMENT '处置时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_diff` (`run_id`,`diff_type`,`biz_id`),
  KEY `idx_recon_diff_open` (`resolve_status`,`severity`),
  KEY `idx_recon_diff_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账差异账本（OPS-01）';

CREATE TABLE `reconciliation_run` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '对账运行ID',
  `business_date` date NOT NULL COMMENT '对账业务日期',
  `scope` varchar(32) NOT NULL COMMENT '对账范围：PAYMENT_ORDER/REFUND/INVENTORY',
  `total_checked` int NOT NULL DEFAULT '0' COMMENT '核对条数',
  `total_diff` int NOT NULL DEFAULT '0' COMMENT '差异数',
  `total_skipped` int NOT NULL DEFAULT '0' COMMENT '不可核验跳过条数（依赖服务不可达等）',
  `lease_owner` varchar(128) DEFAULT NULL COMMENT '租约持有者（执行实例标识）',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '租约到期时刻：RUNNING 超过此值可被接管',
  `heartbeat_at` datetime(3) DEFAULT NULL COMMENT '最近批次心跳时间',
  `payment_cursor` bigint NOT NULL DEFAULT '0' COMMENT '支付正向扫描已处理最大 attemptId',
  `refund_cursor` bigint NOT NULL DEFAULT '0' COMMENT '退款正向扫描已处理最大 refundId',
  `inventory_cursor` bigint NOT NULL DEFAULT '0' COMMENT '库存正向扫描已处理最大 orderId',
  `order_page` int NOT NULL DEFAULT '0' COMMENT '反向订单扫描已完成页码（恢复从下一页开始）',
  `status` varchar(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/DONE/FAILED',
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_run_date_scope` (`business_date`,`scope`),
  KEY `idx_recon_run_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账运行（OPS-01）';

CREATE TABLE `refund_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '退款单ID',
  `refund_no` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '商户退款号（调用方提供，幂等键；同号同参重放返回原结果）',
  `payment_attempt_id` bigint unsigned NOT NULL COMMENT '原成功支付尝试ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `refund_amount` decimal(18,2) NOT NULL COMMENT '退款金额（服务端按实收核算，禁止超退）',
  `currency` varchar(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY' COMMENT '币种（必须与原支付一致）',
  `reason_code` varchar(48) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '退款原因码',
  `status` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'REQUESTED' COMMENT '状态: REQUESTED/PROCESSING/UNKNOWN/SUCCEEDED/FAILED',
  `provider_refund_no` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '渠道退款单号（渠道确认后回填）',
  `error_code` varchar(48) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '失败/未知错误码',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观版本（状态每次迁移 +1）',
  `next_query_at` datetime(3) DEFAULT NULL COMMENT '下次查单时间（UNKNOWN/PROCESSING 收敛）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_order_no` (`refund_no`),
  KEY `idx_refund_order_attempt` (`payment_attempt_id`),
  KEY `idx_refund_order_status` (`status`,`next_query_at`),
  CONSTRAINT `chk_refund_amount` CHECK ((`refund_amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单（渠道退款事实；审批/渠道确认状态分离，T02）';

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
