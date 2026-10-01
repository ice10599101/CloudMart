-- T02：退款单——渠道退款事实与订单审批状态分离。
-- 状态机（12.5）：REQUESTED → PROCESSING → SUCCEEDED / FAILED；结果未知 → UNKNOWN（查单收敛）。
-- 渠道确认才 SUCCEEDED：审批成功不能代替渠道成功；refundNo 幂等（同号重放返回原结果）。
CREATE TABLE IF NOT EXISTS `refund_order` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '退款单ID',
    `refund_no`          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '商户退款号（调用方提供，幂等键；同号同参重放返回原结果）',
    `payment_attempt_id` BIGINT UNSIGNED NOT NULL COMMENT '原成功支付尝试ID',
    `order_id`           BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    `refund_amount`      DECIMAL(18,2) NOT NULL COMMENT '退款金额（服务端按实收核算，禁止超退）',
    `currency`           VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY' COMMENT '币种（必须与原支付一致）',
    `reason_code`        VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '退款原因码',
    `status`             VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'REQUESTED' COMMENT '状态: REQUESTED/PROCESSING/UNKNOWN/SUCCEEDED/FAILED',
    `provider_refund_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '渠道退款单号（渠道确认后回填）',
    `error_code`         VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '失败/未知错误码',
    `version`            INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观版本（状态每次迁移 +1）',
    `next_query_at`      DATETIME(3) DEFAULT NULL COMMENT '下次查单时间（UNKNOWN/PROCESSING 收敛）',
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY `pk_refund_order` (`id`),
    UNIQUE KEY `uk_refund_order_no` (`refund_no`),
    INDEX `idx_refund_order_attempt` (`payment_attempt_id`),
    INDEX `idx_refund_order_status` (`status`, `next_query_at`),
    CONSTRAINT `chk_refund_amount` CHECK (`refund_amount` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '退款单（渠道退款事实；审批/渠道确认状态分离，T02）';
