-- 补偿任务表字段与共享实现对齐：
-- mall-common 的 CompensationTaskEntity / CompensationTaskMapper 使用 next_retry_at，
-- 而 V1 基线建成了 next_run_at，导致补偿调度每轮 SQL 报
-- "Unknown column 'next_retry_at' in 'where clause'"，补偿任务永远无法被领取。
-- 仅重命名列与索引，不改语义、不改数据。

ALTER TABLE `compensation_task`
    CHANGE COLUMN `next_run_at` `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下次重试时间（退避）';

ALTER TABLE `compensation_task`
    DROP INDEX `idx_compensation_status_run`,
    ADD INDEX `idx_compensation_status_retry` (`status`, `next_retry_at`);
