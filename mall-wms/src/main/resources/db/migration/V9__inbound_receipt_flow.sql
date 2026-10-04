-- V9 (T19)：入库收货流水与库存入账事实——同 receiptId 幂等、正数/超收 CAS 约束。

-- 收货流水：每次收货一条事实（操作者/SKU/数量/来源）；receiptId 幂等键。
CREATE TABLE IF NOT EXISTS `inbound_receipt` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `receipt_id`     VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '收货流水ID（调用方生成，幂等键）',
    `inbound_order_id` BIGINT UNSIGNED NOT NULL COMMENT '入库单ID',
    `inbound_item_id`  BIGINT UNSIGNED NOT NULL COMMENT '入库明细ID',
    `sku_id`         BIGINT UNSIGNED NOT NULL COMMENT 'SKU',
    `quantity`       INT NOT NULL COMMENT '本次收货数量（正数）',
    `operator_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '仓管员ID',
    `quality_result` VARCHAR(20) NOT NULL DEFAULT 'PASSED'
        COMMENT '验收质量:PASSED可售/QUARANTINE隔离（T19：破损/隔离分开计）',
    `biz_source`     VARCHAR(40) NOT NULL DEFAULT 'PURCHASE_INBOUND' COMMENT '业务来源:PURCHASE_INBOUND/AFTER_SALE_RETURN',
    `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY `pk_inbound_receipt` (`id`),
    UNIQUE KEY `uk_inbound_receipt_id` (`receipt_id`),
    INDEX `idx_inbound_receipt_order` (`inbound_order_id`, `inbound_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入库收货流水(T19,幂等+库存入账事实)';

-- 库存入账事件 Outbox 状态由公共 outbox_event 承载（本迁移不加表）。
