-- P02（QA35）：购买幂等完成与资产交付不可分离。
-- 1) pet_purchase_order 增加 (user_id, request_key) 唯一与请求摘要：一次认领确定业务 ID，
--    重试/接管不另造订单（uk 兜底，即使应用层路径绕过也不会二次交付）。
-- 2) pet_request_dedup 增加租约与版本：PROCESSING 崩溃残留可被同键重试或恢复扫描器
--    按租约到期 CAS 接管，消除"永久 PROCESSING"。
ALTER TABLE `pet_purchase_order`
    ADD COLUMN `request_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
        COMMENT '客户端请求键(16..128 ASCII,同一次购买意图唯一;uk 兜底防重复建单)' AFTER `wallet_transaction_id`,
    ADD COLUMN `payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
        COMMENT '规范请求摘要SHA-256(与 dedup 行一致,用于事实核对)' AFTER `request_key`,
    ADD UNIQUE KEY `uk_pet_purchase_order_request` (`user_id`, `request_key`);

ALTER TABLE `pet_request_dedup`
    ADD COLUMN `lease_owner` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
        COMMENT '租约持有者(实例+线程标识,接管时轮换)' AFTER `status`,
    ADD COLUMN `lease_until` DATETIME(6) DEFAULT NULL
        COMMENT '租约到期时间(UTC,到期前同键返回处理中,到期后可 CAS 接管)' AFTER `lease_owner`,
    ADD COLUMN `version` BIGINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '乐观版本(接管/终态 CAS 推进)' AFTER `lease_until`,
    ADD INDEX `idx_pet_request_dedup_recovery` (`status`, `lease_until`);
