-- R11：零元订单专用结算标记——全额优惠产生的合法零元单不经过收费渠道，
-- 由订单域内部结算（ZERO_PAY 事实入 Outbox）。对账反向扫描据此跳过
-- "无 SUCCESS 支付凭证"核对（零元单的凭证是订单域 ZERO_PAY 事实而非渠道支付）。
ALTER TABLE orders
    ADD COLUMN zero_pay TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'R11 零元结算标记：全额优惠合法零元单（内部结算，无收费渠道支付单）'
    AFTER refunded_amount;
