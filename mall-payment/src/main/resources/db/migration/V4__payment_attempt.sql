-- PAY-01：支付尝试台账与渠道通知日志——每渠道尝试有稳定商户支付号，
-- 数据库约束禁止同订单并发多笔活动尝试；通知日志唯一防重放。

CREATE TABLE payment_attempt
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '尝试ID',
    order_id            BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    merchant_payment_no VARCHAR(64)     NOT NULL COMMENT '商户支付号（对渠道稳定，唯一）',
    channel             VARCHAR(32)     NOT NULL COMMENT '渠道：MOCK/ALIPAY/WECHAT',
    amount              DECIMAL(18, 2)  NOT NULL COMMENT '应付金额（服务端计算）',
    currency            CHAR(3)         NOT NULL DEFAULT 'CNY' COMMENT '币种（首期 CNY）',
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/CLOSED/FAILED',
    provider_txn_no     VARCHAR(64)     NULL COMMENT '渠道交易号',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观版本',
    expires_at          DATETIME(3)     NULL COMMENT '尝试有效期',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY pk_payment_attempt (id),
    UNIQUE KEY uk_attempt_merchant_no (merchant_payment_no),
    KEY idx_attempt_order (order_id, status),
    CONSTRAINT chk_attempt_amount CHECK (amount > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付尝试台账（PAY-01）';

CREATE TABLE payment_notify_log
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    channel             VARCHAR(32)     NOT NULL COMMENT '渠道',
    notification_id     VARCHAR(128)    NOT NULL COMMENT '渠道通知唯一标识（防重放幂等键）',
    signature_valid     TINYINT(1)      NOT NULL COMMENT '验签结果',
    handle_result       VARCHAR(16)     NOT NULL COMMENT '处理结果：SUCCESS/DUPLICATE/REJECTED/FAILED',
    payment_attempt_id  BIGINT UNSIGNED NULL COMMENT '关联尝试ID',
    detail              VARCHAR(500)    NULL COMMENT '摘要（脱敏，不含敏感原文）',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '接收时间',
    PRIMARY KEY pk_payment_notify_log (id),
    UNIQUE KEY uk_notify_channel_id (channel, notification_id),
    KEY idx_notify_attempt (payment_attempt_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '渠道通知日志（PAY-01）';
