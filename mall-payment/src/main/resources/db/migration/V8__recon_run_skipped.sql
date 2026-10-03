-- T11：对账运行统计细化——total_checked 语义收敛为"有结论的核对"
-- （一致或差异），新增 total_skipped 记录不可核验跳过数（如订单服务
-- 不可达的行），运营可区分"干净"与"没查到"。
ALTER TABLE reconciliation_run
    ADD COLUMN total_skipped INT NOT NULL DEFAULT 0 COMMENT '不可核验跳过条数（依赖服务不可达等）' AFTER total_diff;
