-- R09：支付尝试场景化与请求幂等键。
-- scene：H5/MINIAPP/APP；request_key：客户端创建尝试的稳定幂等键（同键返回同一笔，
-- 异参复用触发 IDEMPOTENCY_KEY_REUSED）。
-- active_order_id：STORED 生成列——仅 PENDING 尝试携带 order_id，唯一索引在数据库层
-- 硬性保证"同订单最多一笔活动尝试"（AT18），关闭/成功后为 NULL 不阻挡历史多行。

ALTER TABLE payment_attempt
    ADD COLUMN scene       VARCHAR(16) NOT NULL DEFAULT 'H5' COMMENT 'R09 支付场景：H5/MINIAPP/APP' AFTER channel,
    ADD COLUMN request_key VARCHAR(64) NULL COMMENT 'R09 创建尝试幂等键（同键稳定返回同一笔尝试）' AFTER scene;

ALTER TABLE payment_attempt
    ADD COLUMN active_order_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (IF(status = 'PENDING', order_id, NULL)) STORED
        COMMENT 'R09 活动尝试约束列：仅 PENDING 时=order_id' AFTER request_key;

ALTER TABLE payment_attempt
    ADD UNIQUE KEY uk_attempt_request_key (request_key),
    ADD UNIQUE KEY uk_attempt_active_order (active_order_id);
