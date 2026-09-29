-- P2-5：钱包账本月度归档汇总——账本无限增长风险的归档层（只写不删）。
-- 物理清理 pet_wallet_ledger 涉及对账基线联动改造（PetWalletReconcileJob 以全量 SUM(delta) 为基线），
-- 删除策略需产品/财务确认后另行启用；本表先行落库，提供归档依据与报表能力。

CREATE TABLE IF NOT EXISTS `pet_wallet_ledger_archive` (
    `id`                 BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `account_id`         BIGINT UNSIGNED NOT NULL COMMENT '钱包账户ID',
    `stat_month`         CHAR(7) NOT NULL COMMENT '归档月份(UTC, YYYY-MM)',
    `entry_count`        BIGINT NOT NULL DEFAULT 0 COMMENT '流水条数',
    `sum_delta`          BIGINT NOT NULL DEFAULT 0 COMMENT '净变动额(delta 合计)',
    `ending_version`     BIGINT UNSIGNED NOT NULL COMMENT '该月末(截至归档时)最大账本版本',
    `ending_balance_after` BIGINT NOT NULL COMMENT '该月末(截至归档时)最后一条 balance_after',
    `archived_at`        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '归档时间(UTC)',
    PRIMARY KEY `pk_pet_wallet_ledger_archive` (`id`),
    UNIQUE KEY `uk_ledger_archive_account_month` (`account_id`, `stat_month`),
    INDEX `idx_ledger_archive_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包账本月度归档汇总(物理清理策略待产品确认)';
