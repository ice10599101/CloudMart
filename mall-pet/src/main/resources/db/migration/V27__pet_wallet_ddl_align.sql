-- V27: W-01/W-04 实体与 DDL 对齐修复——补齐 updatedAt 列
-- 根因：实体生成器统一带 @TableField(fill=INSERT_UPDATE) updatedAt，V22/V23/V24 部分建表
-- 漏建该列，真库上 MyBatis-Plus 生成的 INSERT/SELECT 带 updated_at 字段即报
-- "Unknown column 'updated_at' in 'field list'"（远程已实际发生，钱包流水接口 500）。
-- 全部为追加列（带默认值），对既有数据零影响。

ALTER TABLE `pet_wallet_transaction`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `created_at`;
ALTER TABLE `pet_purchase_order`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `created_at`;
ALTER TABLE `pet_asset_grant`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `created_at`;
ALTER TABLE `pet_reward_claim`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `created_at`;
ALTER TABLE `pet_wallet_adjustment`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `created_at`;
ALTER TABLE `pet_wallet_reconcile_run`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `started_at`;
ALTER TABLE `pet_wallet_reconcile_item`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `created_at`;
ALTER TABLE `pet_wallet_cutover_batch`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `created_at`;
ALTER TABLE `pet_wallet_cutover_item`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `created_at`;
ALTER TABLE `pet_visit_fact`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)' AFTER `created_at`;
