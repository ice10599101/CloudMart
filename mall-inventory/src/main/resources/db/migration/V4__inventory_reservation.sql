-- STOCK-01：订单级库存预占台账——(order_id, sku_id) 唯一，状态机一次性迁移
-- RESERVED → CONFIRMED（确认销售）或 RELEASED（取消/释放），由条件更新保证只成功一次；
-- Redis 仅作加速，DB 为库存权威。

CREATE TABLE inventory_reservation
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预占ID',
    order_id     BIGINT UNSIGNED NOT NULL COMMENT '订单ID（0=历史无单预占，仅排空期兼容）',
    sku_id       BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    quantity     INT             NOT NULL COMMENT '预占数量（正数）',
    status       VARCHAR(20)     NOT NULL DEFAULT 'RESERVED' COMMENT '状态：RESERVED/CONFIRMED/RELEASED',
    version      INT             NOT NULL DEFAULT 0 COMMENT '乐观版本（状态每次迁移 +1）',
    confirmed_at DATETIME(3)     NULL COMMENT '确认时间',
    released_at  DATETIME(3)     NULL COMMENT '释放时间',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY pk_inventory_reservation (id),
    UNIQUE KEY uk_reservation_order_sku (order_id, sku_id),
    KEY idx_reservation_status (status, updated_at),
    KEY idx_reservation_sku (sku_id, status),
    CONSTRAINT chk_reservation_quantity CHECK (quantity > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '库存预占台账';
