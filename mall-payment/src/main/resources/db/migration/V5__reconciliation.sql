-- OPS-01：对账运行与差异账本——支付/订单四方一致性的持久化核对结果。
-- 人工解决不直接改资金，只登记证据（detail/evidence），触发受控修复流程。

CREATE TABLE reconciliation_run
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '对账运行ID',
    business_date   DATE            NOT NULL COMMENT '对账业务日期',
    scope           VARCHAR(32)     NOT NULL COMMENT '对账范围：PAYMENT_ORDER/REFUND/INVENTORY',
    total_checked   INT             NOT NULL DEFAULT 0 COMMENT '核对条数',
    total_diff      INT             NOT NULL DEFAULT 0 COMMENT '差异数',
    status          VARCHAR(16)     NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/DONE/FAILED',
    started_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
    finished_at     DATETIME(3)     NULL COMMENT '结束时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY pk_reconciliation_run (id),
    UNIQUE KEY uk_recon_run_date_scope (business_date, scope),
    KEY idx_recon_run_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '对账运行（OPS-01）';

CREATE TABLE reconciliation_difference
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '差异ID',
    run_id         BIGINT UNSIGNED NOT NULL COMMENT '对账运行ID',
    diff_type      VARCHAR(64)     NOT NULL COMMENT '差异类型（如 PAYMENT_SUCCESS_ORDER_NOT_PAID）',
    biz_id         VARCHAR(64)     NOT NULL COMMENT '业务主键（如 paymentId/orderId）',
    severity       VARCHAR(16)     NOT NULL DEFAULT 'HIGH' COMMENT 'HIGH/MEDIUM/LOW',
    detail         VARCHAR(1000)   NULL COMMENT '差异详情（脱敏）',
    evidence       VARCHAR(1000)   NULL COMMENT '证据（两侧状态快照 JSON）',
    resolve_status VARCHAR(16)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/ACCEPTED',
    resolved_by    BIGINT UNSIGNED NULL COMMENT '处置管理员',
    resolve_note   VARCHAR(500)    NULL COMMENT '处置说明',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发现时间',
    resolved_at    DATETIME(3)     NULL COMMENT '处置时间',
    PRIMARY KEY pk_reconciliation_difference (id),
    UNIQUE KEY uk_recon_diff (run_id, diff_type, biz_id),
    KEY idx_recon_diff_open (resolve_status, severity),
    KEY idx_recon_diff_run (run_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '对账差异账本（OPS-01）';
