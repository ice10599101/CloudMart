-- CloudMart mall-payment 初始 Schema 基线

CREATE TABLE IF NOT EXISTS `payments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '支付记录ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `payment_no` varchar(64) NOT NULL COMMENT '支付流水号',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `pay_method` varchar(32) NOT NULL DEFAULT 'ALIPAY' COMMENT '支付方式: ALIPAY/WECHAT/MOCK',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '支付状态: PENDING/SUCCESS/FAILED/REFUNDED',
  `paid_at` datetime DEFAULT NULL COMMENT '支付完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付记录表';
