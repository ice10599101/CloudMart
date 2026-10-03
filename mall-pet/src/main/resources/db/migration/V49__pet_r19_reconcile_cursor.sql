-- V49 (R19): 对账运行可恢复游标——keyset 分批推进 + 固定本轮上界
-- 原缺陷：固定 LIMIT 5000 从头扫描——第 5001+ 账户永久不覆盖；
-- 中断后无游标，只能整轮重来。

ALTER TABLE `pet_wallet_reconcile_run`
    ADD COLUMN `max_account_id` BIGINT UNSIGNED DEFAULT NULL
        COMMENT '本轮账户上界(创建时快照 MAX(id)；该轮只扫 <= 此值)' AFTER `cutoff`,
    ADD COLUMN `cursor_account_id` BIGINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '已扫描到的账户ID游标(keyset 推进，中断后续跑)' AFTER `max_account_id`;
