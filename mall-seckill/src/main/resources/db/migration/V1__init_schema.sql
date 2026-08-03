-- CloudMart mall-seckill 初始 Schema 基线

CREATE TABLE IF NOT EXISTS `seckill_activities` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '活动ID',
  `name` varchar(100) NOT NULL COMMENT '活动名称',
  `description` varchar(500) DEFAULT NULL COMMENT '活动描述',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `status` varchar(20) NOT NULL DEFAULT 'UPCOMING' COMMENT '状态: UPCOMING-未开始, ONGOING-进行中, ENDED-已结束',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀活动表';

CREATE TABLE IF NOT EXISTS `seckill_products` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '秒杀商品ID',
  `activity_id` bigint unsigned NOT NULL COMMENT '活动ID',
  `sku_id` bigint unsigned NOT NULL COMMENT '商品SKU ID',
  `seckill_price` decimal(10,2) NOT NULL COMMENT '秒杀价格',
  `original_price` decimal(10,2) NOT NULL COMMENT '原价',
  `total_stock` int unsigned NOT NULL COMMENT '秒杀库存总量',
  `available_stock` int unsigned NOT NULL COMMENT '剩余秒杀库存',
  `per_user_limit` int unsigned NOT NULL DEFAULT '1' COMMENT '每人限购数量',
  `status` varchar(20) NOT NULL DEFAULT 'ON_SHELF' COMMENT '状态: ON_SHELF-上架, OFF_SHELF-下架',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_sku` (`activity_id`,`sku_id`),
  KEY `idx_activity_id` (`activity_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀商品表';
