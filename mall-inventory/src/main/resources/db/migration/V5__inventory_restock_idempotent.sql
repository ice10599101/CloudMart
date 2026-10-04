-- V5 (T19)：库存入账幂等——receipt_id 关联收货流水，uk 兜底重复入账。

ALTER TABLE `inventory_logs`
    ADD COLUMN `receipt_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
        COMMENT '来源收货流水ID（T19：RESTOCK 入账幂等键）' AFTER order_id;

ALTER TABLE `inventory_logs`
    ADD UNIQUE KEY `uk_inventory_logs_receipt` (`receipt_id`);
