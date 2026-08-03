-- CloudMart mall-inventory 初始 Schema 基线

CREATE TABLE IF NOT EXISTS `inventory` (
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

CREATE TABLE IF NOT EXISTS `inventory_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `type` varchar(20) NOT NULL COMMENT '操作类型: DEDUCT/RELEASE/CONFIRM',
  `quantity` int unsigned NOT NULL COMMENT '操作数量',
  `order_id` bigint unsigned DEFAULT NULL COMMENT '关联订单ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存操作日志';
