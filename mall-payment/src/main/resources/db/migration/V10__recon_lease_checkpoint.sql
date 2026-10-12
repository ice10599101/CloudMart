-- R26：对账运行租约与断点恢复。
-- ① 执行租约 lease_owner/lease_until/heartbeat_at：RUNNING 持有者心跳续期，
--    进程死亡后租约到期可被其他执行器 CAS 接管（同日恢复，不重复双跑）；
-- ② 断点游标：各正向扫描 last-id 游标 + 反向订单扫描已完成页码，
--    PARTIAL/FAILED/失联接管后从断点续扫，已核对计数不重计。
ALTER TABLE reconciliation_run
    ADD COLUMN lease_owner VARCHAR(128) NULL COMMENT '租约持有者（执行实例标识）' AFTER total_skipped,
    ADD COLUMN lease_until DATETIME(3) NULL COMMENT '租约到期时刻：RUNNING 超过此值可被接管' AFTER lease_owner,
    ADD COLUMN heartbeat_at DATETIME(3) NULL COMMENT '最近批次心跳时间' AFTER lease_until,
    ADD COLUMN payment_cursor BIGINT NOT NULL DEFAULT 0 COMMENT '支付正向扫描已处理最大 attemptId' AFTER heartbeat_at,
    ADD COLUMN refund_cursor BIGINT NOT NULL DEFAULT 0 COMMENT '退款正向扫描已处理最大 refundId' AFTER payment_cursor,
    ADD COLUMN inventory_cursor BIGINT NOT NULL DEFAULT 0 COMMENT '库存正向扫描已处理最大 orderId' AFTER refund_cursor,
    ADD COLUMN order_page INT NOT NULL DEFAULT 0 COMMENT '反向订单扫描已完成页码（恢复从下一页开始）' AFTER inventory_cursor;
