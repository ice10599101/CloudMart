-- CloudMart 订单分表迁移 (ShardingSphere 按 user_id 取模分 4 表)
-- 注意: 本脚本仅在 ShardingSphere 未启用时由 Flyway 直接执行
-- 当 ShardingSphere 启用后, 由 ShardingSphere 管理路由, Flyway 对物理表直接执行

-- orders 分表
CREATE TABLE IF NOT EXISTS `orders_0` (
  `id` bigint unsigned NOT NULL COMMENT '订单ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额',
  `coupon_id` bigint unsigned DEFAULT NULL COMMENT '使用的优惠券ID',
  `activity_id` bigint unsigned DEFAULT NULL COMMENT '秒杀活动ID',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货人手机号',
  `receiver_address` varchar(512) NOT NULL COMMENT '收货地址',
  `shipped_at` datetime DEFAULT NULL COMMENT '发货时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `refund_reason` varchar(512) DEFAULT NULL COMMENT '退款原因',
  `refund_reject_reason` varchar(512) DEFAULT NULL COMMENT '退款拒绝原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单分表0';

CREATE TABLE IF NOT EXISTS `orders_1` (
  `id` bigint unsigned NOT NULL COMMENT '订单ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额',
  `coupon_id` bigint unsigned DEFAULT NULL COMMENT '使用的优惠券ID',
  `activity_id` bigint unsigned DEFAULT NULL COMMENT '秒杀活动ID',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货人手机号',
  `receiver_address` varchar(512) NOT NULL COMMENT '收货地址',
  `shipped_at` datetime DEFAULT NULL COMMENT '发货时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `refund_reason` varchar(512) DEFAULT NULL COMMENT '退款原因',
  `refund_reject_reason` varchar(512) DEFAULT NULL COMMENT '退款拒绝原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单分表1';

CREATE TABLE IF NOT EXISTS `orders_2` (
  `id` bigint unsigned NOT NULL COMMENT '订单ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额',
  `coupon_id` bigint unsigned DEFAULT NULL COMMENT '使用的优惠券ID',
  `activity_id` bigint unsigned DEFAULT NULL COMMENT '秒杀活动ID',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货人手机号',
  `receiver_address` varchar(512) NOT NULL COMMENT '收货地址',
  `shipped_at` datetime DEFAULT NULL COMMENT '发货时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `refund_reason` varchar(512) DEFAULT NULL COMMENT '退款原因',
  `refund_reject_reason` varchar(512) DEFAULT NULL COMMENT '退款拒绝原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单分表2';

CREATE TABLE IF NOT EXISTS `orders_3` (
  `id` bigint unsigned NOT NULL COMMENT '订单ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额',
  `coupon_id` bigint unsigned DEFAULT NULL COMMENT '使用的优惠券ID',
  `activity_id` bigint unsigned DEFAULT NULL COMMENT '秒杀活动ID',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态',
  `receiver_name` varchar(64) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货人手机号',
  `receiver_address` varchar(512) NOT NULL COMMENT '收货地址',
  `shipped_at` datetime DEFAULT NULL COMMENT '发货时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `refund_reason` varchar(512) DEFAULT NULL COMMENT '退款原因',
  `refund_reject_reason` varchar(512) DEFAULT NULL COMMENT '退款拒绝原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单分表3';

-- order_items 分表
CREATE TABLE IF NOT EXISTS `order_items_0` (
  `id` bigint unsigned NOT NULL COMMENT '订单项ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称快照',
  `sku_image` varchar(512) DEFAULT NULL COMMENT 'SKU图片快照',
  `sku_attributes` varchar(512) DEFAULT NULL COMMENT 'SKU属性快照',
  `price` decimal(10,2) NOT NULL COMMENT '下单时单价',
  `quantity` int unsigned NOT NULL COMMENT '购买数量',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单项分表0';

CREATE TABLE IF NOT EXISTS `order_items_1` (
  `id` bigint unsigned NOT NULL COMMENT '订单项ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称快照',
  `sku_image` varchar(512) DEFAULT NULL COMMENT 'SKU图片快照',
  `sku_attributes` varchar(512) DEFAULT NULL COMMENT 'SKU属性快照',
  `price` decimal(10,2) NOT NULL COMMENT '下单时单价',
  `quantity` int unsigned NOT NULL COMMENT '购买数量',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单项分表1';

CREATE TABLE IF NOT EXISTS `order_items_2` (
  `id` bigint unsigned NOT NULL COMMENT '订单项ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称快照',
  `sku_image` varchar(512) DEFAULT NULL COMMENT 'SKU图片快照',
  `sku_attributes` varchar(512) DEFAULT NULL COMMENT 'SKU属性快照',
  `price` decimal(10,2) NOT NULL COMMENT '下单时单价',
  `quantity` int unsigned NOT NULL COMMENT '购买数量',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单项分表2';

CREATE TABLE IF NOT EXISTS `order_items_3` (
  `id` bigint unsigned NOT NULL COMMENT '订单项ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `product_name` varchar(256) NOT NULL COMMENT '商品名称快照',
  `sku_image` varchar(512) DEFAULT NULL COMMENT 'SKU图片快照',
  `sku_attributes` varchar(512) DEFAULT NULL COMMENT 'SKU属性快照',
  `price` decimal(10,2) NOT NULL COMMENT '下单时单价',
  `quantity` int unsigned NOT NULL COMMENT '购买数量',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单项分表3';

-- Seata AT 模式 undo_log 表 (每个参与分布式事务的数据库都需要)
CREATE TABLE IF NOT EXISTS `undo_log` (
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
