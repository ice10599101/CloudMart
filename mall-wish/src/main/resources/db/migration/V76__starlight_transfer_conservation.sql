-- =============================================
-- V76: R06 星光转赠守恒改造（与 V63 同域收敛）
--   1) starlight_transfer 扩展：请求键唯一（持久幂等，不再依赖 Redis 过滤器）、
--      状态与等额对转审计（sender/receiver 流水 ID、credited_amount）——
--      SETTLED 要求 amount = creditedAmount，双边流水等额异号。
--   2) starlight_daily_out_quota：日转出额度权威行 (user_id, business_date) 唯一，
--      条件更新 used_amount + amount <= 200 原子占用（替换"先汇总再写"的非原子口径）。
--   business_date 采用服务端 Asia/Shanghai 业务日（R22 经济日界统一）。
-- =============================================
ALTER TABLE starlight_transfer
    ADD COLUMN request_key VARCHAR(64) NULL COMMENT '请求键（重放幂等；同键异载荷 409）' AFTER amount,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SETTLED' COMMENT 'SETTLED=等额对转完成' AFTER request_key,
    ADD COLUMN business_date DATE NULL COMMENT '业务日（Asia/Shanghai，日额度归属）' AFTER status,
    ADD COLUMN credited_amount INT NULL COMMENT '接收方实际入账（守恒要求=amount）' AFTER business_date,
    ADD COLUMN sender_ledger_id BIGINT UNSIGNED NULL COMMENT '转出流水 ID' AFTER credited_amount,
    ADD COLUMN receiver_ledger_id BIGINT UNSIGNED NULL COMMENT '转入流水 ID' AFTER sender_ledger_id,
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER receiver_ledger_id,
    ADD UNIQUE INDEX uk_from_request (from_user_id, request_key);

CREATE TABLE IF NOT EXISTS starlight_daily_out_quota (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '转出用户 ID',
    business_date DATE NOT NULL COMMENT '业务日（Asia/Shanghai）',
    used_amount INT NOT NULL DEFAULT 0 COMMENT '当日已转出累计',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_user_date (user_id, business_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '星光日转出额度（R06 原子占用）';
