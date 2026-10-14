-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `compensation_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` varchar(128) NOT NULL COMMENT '业务幂等键',
  `action` varchar(64) NOT NULL COMMENT '处理动作（handler 注册键）',
  `aggregate_id` varchar(64) NOT NULL COMMENT '聚合根ID',
  `payload` text COMMENT '载荷JSON',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/DEAD_LETTER',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_run_at` datetime(3) DEFAULT NULL COMMENT '下次执行时间（退避）',
  `last_error` varchar(1024) DEFAULT NULL COMMENT '最后错误（脱敏）',
  `locked_by` varchar(64) DEFAULT NULL COMMENT '执行者实例ID（租约）',
  `locked_at` datetime(3) DEFAULT NULL COMMENT '租约时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compensation_task_id` (`task_id`),
  KEY `idx_compensation_status_run` (`status`,`next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='WMS补偿任务（WMS-01）';

CREATE TABLE `inbound_order_items` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `inbound_order_id` bigint unsigned NOT NULL COMMENT '入库单ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称',
  `expected_quantity` int NOT NULL COMMENT '预期数量',
  `received_quantity` int NOT NULL DEFAULT '0' COMMENT '实际收货数量',
  `location_code` varchar(64) DEFAULT NULL COMMENT '入库库位',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_inbound_order_items_inbound` (`inbound_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入库明细表';

CREATE TABLE `inbound_orders` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `warehouse_id` bigint unsigned NOT NULL COMMENT '仓库ID',
  `type` varchar(20) NOT NULL DEFAULT 'PURCHASE' COMMENT '类型: PURCHASE-采购入库/RETURN-退货入库/TRANSFER-调拨入库',
  `reference_no` varchar(64) DEFAULT NULL COMMENT '关联单号（采购单号/退货单号）',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待入库/RECEIVING-入库中/COMPLETED-已完成',
  `total_quantity` int NOT NULL DEFAULT '0' COMMENT '总数量',
  `received_quantity` int NOT NULL DEFAULT '0' COMMENT '已收数量',
  `operator_user_id` bigint unsigned DEFAULT NULL COMMENT '操作员ID',
  `completed_time` datetime DEFAULT NULL COMMENT '完成时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbound_orders_type_ref` (`type`,`reference_no`),
  KEY `idx_inbound_orders_warehouse` (`warehouse_id`),
  KEY `idx_inbound_orders_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入库单表';

CREATE TABLE `inbound_receipt` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `receipt_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '收货流水ID（调用方生成，幂等键）',
  `inbound_order_id` bigint unsigned NOT NULL COMMENT '入库单ID',
  `inbound_item_id` bigint unsigned NOT NULL COMMENT '入库明细ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU',
  `quantity` int NOT NULL COMMENT '本次收货数量（正数）',
  `operator_id` bigint unsigned DEFAULT NULL COMMENT '仓管员ID',
  `quality_result` varchar(20) NOT NULL DEFAULT 'PASSED' COMMENT '验收质量:PASSED可售/QUARANTINE隔离（T19：破损/隔离分开计）',
  `biz_source` varchar(40) NOT NULL DEFAULT 'PURCHASE_INBOUND' COMMENT '业务来源:PURCHASE_INBOUND/AFTER_SALE_RETURN',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbound_receipt_id` (`receipt_id`),
  KEY `idx_inbound_receipt_order` (`inbound_order_id`,`inbound_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入库收货流水(T19,幂等+库存入账事实)';

CREATE TABLE `inbox_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `consumer` varchar(64) NOT NULL COMMENT '消费者标识',
  `event_id` varchar(64) NOT NULL COMMENT '事件ID（幂等键）',
  `status` varchar(16) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/COMPLETED/FAILED',
  `error` varchar(1024) DEFAULT NULL COMMENT '失败原因（脱敏）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbox_consumer_event` (`consumer`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='WMS事件Inbox（WMS-01）';

CREATE TABLE `outbox_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` varchar(64) NOT NULL COMMENT '全局唯一事件ID（UUID）',
  `event_type` varchar(128) NOT NULL COMMENT '事件类型（如 ORDER_SHIPPED）',
  `schema_version` int NOT NULL DEFAULT '1' COMMENT '载荷结构版本',
  `aggregate_id` varchar(64) NOT NULL COMMENT '聚合根ID（订单ID）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='WMS事件Outbox（WMS-01）';

CREATE TABLE `pick_order_items` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pick_order_id` bigint unsigned NOT NULL COMMENT '拣货单ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称',
  `sku_attributes` varchar(256) DEFAULT NULL COMMENT 'SKU属性',
  `quantity` int NOT NULL COMMENT '数量',
  `location_code` varchar(64) DEFAULT NULL COMMENT '库位编码',
  `picked_quantity` int NOT NULL DEFAULT '0' COMMENT '已拣数量',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_pick_order_items_pick` (`pick_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拣货明细表';

CREATE TABLE `pick_orders` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint unsigned NOT NULL COMMENT '关联订单ID',
  `warehouse_id` bigint unsigned NOT NULL COMMENT '仓库ID',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待拣货/PICKING-拣货中/PICKED-已拣货/PACKED-已打包/SHIPPED-已发货',
  `assigned_user_id` bigint unsigned DEFAULT NULL COMMENT '分配的拣货员ID',
  `pick_time` datetime DEFAULT NULL COMMENT '开始拣货时间',
  `packed_time` datetime DEFAULT NULL COMMENT '打包完成时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pick_orders_order` (`order_id`),
  KEY `idx_pick_orders_order` (`order_id`),
  KEY `idx_pick_orders_warehouse` (`warehouse_id`),
  KEY `idx_pick_orders_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拣货作业单表';

CREATE TABLE `shipping_orders` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint unsigned NOT NULL COMMENT '关联订单ID',
  `warehouse_id` bigint unsigned NOT NULL COMMENT '仓库ID',
  `shipping_no` varchar(64) DEFAULT NULL COMMENT '物流单号',
  `carrier` varchar(50) DEFAULT NULL COMMENT '承运商',
  `tracking_no` varchar(64) DEFAULT NULL COMMENT '承运商运单号（真实单号，建档必填）',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待发货/SHIPPED-已发货/DELIVERED-已签收',
  `shipped_at` datetime(3) DEFAULT NULL COMMENT '出库时间',
  `receiver_name` varchar(50) DEFAULT NULL COMMENT '收件人姓名',
  `receiver_phone` varchar(20) DEFAULT NULL COMMENT '收件人电话',
  `receiver_address` varchar(500) DEFAULT NULL COMMENT '收件人地址',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipping_order` (`order_id`),
  KEY `idx_shipping_orders_order` (`order_id`),
  KEY `idx_shipping_orders_warehouse` (`warehouse_id`),
  KEY `idx_shipping_orders_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流订单表';

CREATE TABLE `shipping_trackings` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shipping_order_id` bigint unsigned NOT NULL COMMENT '物流订单ID',
  `location` varchar(200) DEFAULT NULL COMMENT '所在地点',
  `description` varchar(500) DEFAULT NULL COMMENT '物流描述',
  `happened_at` datetime NOT NULL COMMENT '发生时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_shipping_trackings_order` (`shipping_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流轨迹表';

CREATE TABLE `warehouses` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '仓库名称',
  `address` varchar(500) DEFAULT NULL COMMENT '仓库地址',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-正常',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仓库表';
