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

CREATE TABLE `group_activities` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '活动名称',
  `description` text COMMENT '活动描述',
  `product_id` bigint unsigned NOT NULL COMMENT '关联商品ID',
  `sku_id` bigint unsigned NOT NULL COMMENT '关联SKU ID',
  `original_price` decimal(10,2) NOT NULL COMMENT '原价',
  `group_price` decimal(10,2) NOT NULL COMMENT '拼团价',
  `target_number` int NOT NULL COMMENT '成团所需人数',
  `max_groups` int NOT NULL DEFAULT '0' COMMENT '最大开团数，0=不限',
  `current_groups` int NOT NULL DEFAULT '0' COMMENT '当前已开团数',
  `per_user_limit` int NOT NULL DEFAULT '1' COMMENT '每人限参团数',
  `start_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `status` varchar(20) NOT NULL DEFAULT 'DISABLED' COMMENT '状态: ENABLED/DISABLED/ENDED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_group_activities_product` (`product_id`),
  KEY `idx_group_activities_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团活动表';

CREATE TABLE `group_members` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_order_id` bigint unsigned NOT NULL COMMENT '拼团组ID',
  `user_id` bigint unsigned NOT NULL COMMENT '参与用户ID',
  `activity_id` bigint unsigned NOT NULL COMMENT '拼团活动ID',
  `is_leader` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否团长: 0-否 1-是',
  `address_id` bigint unsigned DEFAULT NULL COMMENT '参团时选定的收货地址ID（成团建单地址快照权威）',
  `receiver_name` varchar(64) DEFAULT NULL COMMENT '收货人快照',
  `receiver_phone` varchar(32) DEFAULT NULL COMMENT '收货电话快照',
  `receiver_address` varchar(500) DEFAULT NULL COMMENT '收货地址快照',
  `order_task_status` varchar(20) DEFAULT NULL COMMENT '建单任务:PENDING/SUCCEEDED/FAILED_ADDRESS/FAILED/ABSENT_ADDRESS',
  `order_task_error` varchar(200) DEFAULT NULL COMMENT '建单失败原因（脱敏）',
  `order_id` bigint unsigned DEFAULT NULL COMMENT '关联订单ID（成团后生成）',
  `status` varchar(20) NOT NULL DEFAULT 'JOINED' COMMENT '状态: JOINED-已加入/CONFIRMED-已确认/REFUNDED-已退款',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_members_user_group` (`user_id`,`group_order_id`),
  UNIQUE KEY `uk_group_members_activity_user` (`activity_id`,`user_id`),
  KEY `idx_group_members_group` (`group_order_id`),
  KEY `idx_group_members_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团参与记录表';

CREATE TABLE `group_orders` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint unsigned NOT NULL COMMENT '拼团活动ID',
  `leader_user_id` bigint unsigned NOT NULL COMMENT '团长用户ID',
  `current_number` int NOT NULL DEFAULT '1' COMMENT '当前参与人数',
  `target_number` int NOT NULL COMMENT '成团所需人数',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-拼团中/SUCCESS-已成团/FAILED-已失败/EXPIRED-已过期',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本：终态迁移 CAS 竞争裁决',
  `expire_time` datetime NOT NULL COMMENT '拼团过期时间',
  `success_time` datetime DEFAULT NULL COMMENT '成团时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_group_orders_activity` (`activity_id`),
  KEY `idx_group_orders_leader` (`leader_user_id`),
  KEY `idx_group_orders_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团组表';

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

CREATE TABLE `tiered_promotion_rules` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `promotion_id` bigint unsigned NOT NULL COMMENT '满减活动ID',
  `min_amount` decimal(10,2) NOT NULL COMMENT '最低消费金额',
  `discount_amount` decimal(10,2) NOT NULL COMMENT '优惠金额',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tiered_promotion_rules_promotion` (`promotion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶梯满减规则表';

CREATE TABLE `tiered_promotions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '活动名称',
  `description` text COMMENT '活动描述',
  `start_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `status` varchar(20) NOT NULL DEFAULT 'DISABLED' COMMENT '状态: ENABLED/DISABLED/ENDED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tiered_promotions_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶梯满减活动表';
