-- PET-10 (T20/T21/T22)：事实回执原子化与可靠重放——回执状态机扩展
-- 回执 APPLIED 与进度累加必须同事务提交；投影失败保留 FAILED 与重试时间，
-- 由调度重试/管理重放恢复（主动作不因投影失败回滚，也不丢进度）。

ALTER TABLE `pet_quest_event_receipt`
    ADD COLUMN `attempts` INT NOT NULL DEFAULT 0 COMMENT '投影已尝试次数（重放/调度重试累加）' AFTER `status`,
    ADD COLUMN `next_retry_at` DATETIME NULL COMMENT '下次自动重试时间(UTC)；NULL 表示无待重试' AFTER `attempts`,
    ADD COLUMN `last_error` VARCHAR(500) NULL COMMENT '最近一次投影失败原因（截断 500 字符）' AFTER `next_retry_at`;

-- 存量状态语义兼容：APPLIED/SKIPPED_STALE 保留；新增 FAILED（投影失败待重试）
ALTER TABLE `pet_quest_event_receipt`
    ADD INDEX `idx_quest_receipt_retry` (`status`, `next_retry_at`, `id`);
