-- 对账 500 修正（远程联调验收发现）：pet_wallet_reconcile_run 缺 created_at 列——
-- 实体 createdAt 标 FieldFill.INSERT，插入时带上该列导致 Unknown column 500（V27 只补了 updated_at）。
-- 与实体/同族表（pet_wallet_reconcile_item 等）对齐补回。

ALTER TABLE `pet_wallet_reconcile_run`
    ADD COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)' AFTER `started_at`;
