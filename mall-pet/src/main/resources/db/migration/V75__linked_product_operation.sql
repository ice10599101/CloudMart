-- =============================================
-- V75: R01/R02 商品权益核验改造（与 V74 同域收敛）
--   1) pet_linked_product_operation：可恢复核销操作台账——请求唯一 (user_id, request_key)，
--      状态 VERIFYING/DELIVERED/REJECTED/PENDING；跨服务核验超时留 PENDING，
--      按 operationId 重查或后台原单重试，不允许跳过资格校验直接造权益。
--   2) pet_feed_entitlement 扩展（6.3 数据约束）：
--      - 一份权益一行：uk(source_fact_id, unit_sequence)，一次发多份用 unit_sequence 区分
--      - status 收敛为 AVAILABLE/CONSUMED/EXPIRED/REVOKED（替换 V74 的 used 布尔，
--        开发期无历史数据，直接 DROP 旧列，不保留双轨）
--      - consumed_by_operation_id 唯一：同一喂食操作键最多占用一份权益（R02 防重）
--      - expires_at/revoked 支撑退款撤销与有效期判定（服务端权威）
--   币种/事实：source_fact_id 指向 mall-coupon exchange_codes.id（核验事实），
--   source_order_item_id 预留订单绑定码扩展，当前业务未启用（NULL 合法）。
-- =============================================
CREATE TABLE IF NOT EXISTS pet_linked_product_operation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    operation_id VARCHAR(64) NOT NULL COMMENT '核销操作 ID（对外不透明字符串）',
    request_key VARCHAR(64) NULL COMMENT '客户端请求键（重放判重）',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '发起核销的用户',
    redemption_code VARCHAR(64) NOT NULL COMMENT '兑换码',
    coupon_fact_id BIGINT UNSIGNED NULL COMMENT 'coupon exchange_codes.id（核验通过后回填）',
    coupon_template_id BIGINT UNSIGNED NULL COMMENT '兑换码关联模板（权益配置推导用）',
    entitlement_id BIGINT UNSIGNED NULL COMMENT '核销成功后发放的权益 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'VERIFYING' COMMENT 'VERIFYING/DELIVERED/REJECTED/PENDING',
    reject_reason_code VARCHAR(64) NULL COMMENT '拒绝原因码（PET_CODE_*）',
    last_error VARCHAR(500) NULL COMMENT '最近一次核验/发放错误（PENDING 恢复依据）',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_operation_id (operation_id),
    UNIQUE INDEX uk_user_request (user_id, request_key),
    INDEX idx_user_status (user_id, status),
    INDEX idx_code (redemption_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品权益核销操作台账（R01）';

ALTER TABLE pet_feed_entitlement
    ADD COLUMN source_fact_id BIGINT UNSIGNED NULL COMMENT 'coupon exchange_codes.id（核验事实）' AFTER user_coupon_id,
    ADD COLUMN unit_sequence INT NOT NULL DEFAULT 1 COMMENT '同一核验事实的第几份权益（一份一行）' AFTER source_fact_id,
    ADD COLUMN source_order_item_id BIGINT UNSIGNED NULL COMMENT '来源订单行（订单绑定码扩展，当前 NULL）' AFTER unit_sequence,
    ADD COLUMN expires_at DATETIME NULL COMMENT '失效时刻（NULL=不失效，服务端判定）' AFTER item_code,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/CONSUMED/EXPIRED/REVOKED' AFTER expires_at,
    ADD COLUMN consumed_by_operation_id VARCHAR(64) NULL COMMENT '占用该份的喂食操作键（唯一，防重）' AFTER status,
    ADD COLUMN consumed_pet_id BIGINT UNSIGNED NULL COMMENT '消耗时宠物 ID' AFTER consumed_by_operation_id,
    ADD COLUMN consumed_at DATETIME NULL COMMENT '消耗时间' AFTER consumed_pet_id,
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER consumed_at,
    ADD UNIQUE INDEX uk_source_fact_unit (source_fact_id, unit_sequence),
    ADD UNIQUE INDEX uk_feed_operation (consumed_by_operation_id),
    ADD INDEX idx_user_status_exp (user_id, status, expires_at);

-- V74 的 used 布尔语义由 status/consumed_at 收敛（开发期无真实数据，不保留双轨）
ALTER TABLE pet_feed_entitlement
    DROP COLUMN used,
    DROP COLUMN used_at;
