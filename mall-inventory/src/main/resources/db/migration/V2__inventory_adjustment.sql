-- TRADE-02：盘点调整单——库存只能经调整单变更（初始化仅一次，补货走入库），全量审计
-- 1) 乐观锁版本：调整单 CAS 前置条件（expectedVersion），并发调整只一个成功
ALTER TABLE `inventory`
    ADD COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本（调整单 CAS 前置条件）' AFTER `reserved`;

-- 2) 调整单台账：request_key 幂等键唯一；前后值快照可审计
CREATE TABLE `inventory_adjustment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '调整单ID',
  `adjust_no` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调整单号（ADJ+日期+序列）',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID（冗余，便于检索）',
  `delta` int NOT NULL COMMENT '调整量（正盘盈/负盘亏，非 0）',
  `reason` varchar(255) NOT NULL COMMENT '调整原因（盘点/损耗/纠错等）',
  `request_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等键（客户端生成，同键同参返回原单）',
  `expected_version` int unsigned NOT NULL COMMENT '提交时期望的乐观锁版本',
  `before_available` int NOT NULL COMMENT '调整前可用库存',
  `after_available` int NOT NULL COMMENT '调整后可用库存',
  `before_reserved` int NOT NULL COMMENT '调整前预占库存（只读快照，调整不改预占）',
  `after_reserved` int NOT NULL COMMENT '调整后预占库存（= before_reserved）',
  `operator_id` bigint unsigned NOT NULL COMMENT '操作管理员 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adjust_no` (`adjust_no`),
  UNIQUE KEY `uk_request_key` (`request_key`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存调整单（TRADE-02：唯一受控变更入口）';
