-- V63 (§8.2): 对账差异人工处置——resolve 记录调查结论/关联补偿单，不直接改账本

ALTER TABLE `pet_wallet_reconcile_item`
    ADD COLUMN `resolution_note` VARCHAR(500) DEFAULT NULL COMMENT '处置结论（必填，调查说明或关联补偿单号）' AFTER `status`,
    ADD COLUMN `resolved_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '处置管理员（mall-admin 认证主体）' AFTER `resolution_note`,
    ADD COLUMN `resolved_at` DATETIME DEFAULT NULL COMMENT '处置时间(UTC)' AFTER `resolved_by`;

CREATE INDEX idx_reconcile_item_status
    ON pet_wallet_reconcile_item (`status`);
