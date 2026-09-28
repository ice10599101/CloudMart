-- TRADE-01：服务端报价单——价格快照由服务端生成，前端金额不参与记账。
-- 报价带 owner 与有效期（默认 5 分钟）；下单必须引用有效报价，价格以快照为准。

CREATE TABLE order_quote
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '报价ID（对外 string）',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    version        INT             NOT NULL DEFAULT 1 COMMENT '报价版本（创建后不可变）',
    status         VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/CONSUMED/EXPIRED',
    total_amount   DECIMAL(18, 2)  NOT NULL COMMENT '商品总额（不含优惠）',
    discount_amount DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    pay_amount     DECIMAL(18, 2)  NOT NULL COMMENT '应付金额',
    coupon_id      BIGINT UNSIGNED NULL COMMENT '报价时锁定的用户优惠券',
    expires_at     DATETIME(3)     NOT NULL COMMENT '过期时间（默认创建 +5 分钟）',
    consumed_by    BIGINT UNSIGNED NULL COMMENT '消费该报价的订单ID',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY pk_order_quote (id),
    KEY idx_quote_user (user_id, created_at),
    KEY idx_quote_status_exp (status, expires_at),
    CONSTRAINT chk_quote_amounts CHECK (total_amount >= 0 AND discount_amount >= 0 AND pay_amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '订单报价单';

CREATE TABLE order_quote_item
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '报价项ID',
    quote_id     BIGINT UNSIGNED NOT NULL COMMENT '所属报价',
    product_id   BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    sku_id       BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    product_name VARCHAR(255)    NULL COMMENT '商品名称快照',
    sku_image    VARCHAR(512)    NULL COMMENT 'SKU 图片快照',
    sku_attributes VARCHAR(512)  NULL COMMENT 'SKU 属性快照',
    price        DECIMAL(18, 2)  NOT NULL COMMENT '报价时权威单价快照',
    quantity     INT             NOT NULL COMMENT '数量（1-999）',
    subtotal     DECIMAL(18, 2)  NOT NULL COMMENT '小计 = price * quantity',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY pk_order_quote_item (id),
    KEY idx_quote_item_quote (quote_id),
    CONSTRAINT chk_quote_item_qty CHECK (quantity > 0),
    CONSTRAINT chk_quote_item_price CHECK (price >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '订单报价明细（快照）';
