-- CloudMart mall-coupon 兑换码表
-- 用于"指定发放"模式下的优惠券兑换码管理，支持序列+校验码生成与 BitMap 防重兑

CREATE TABLE IF NOT EXISTS `exchange_codes` (
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
  KEY `idx_template_serial` (`template_id`, `serial_number`),
  KEY `idx_status` (`status`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_exchange_batch` (`exchange_batch`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券兑换码表';
