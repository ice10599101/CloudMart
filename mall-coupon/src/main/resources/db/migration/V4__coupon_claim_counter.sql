-- COUPON-01：领券计数台账——每人每模板一行，原子条件递增作为限领的权威判定。
-- 修复断点：原实现用 Redis 预减 + selectCount 判断限领，锁在事务提交前释放，
-- 并发领取可突破 per_user_limit；计数台账以 DB 原子更新兜底，正确性不再依赖锁。

CREATE TABLE coupon_claim_counter
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '计数ID',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    template_id   BIGINT UNSIGNED NOT NULL COMMENT '券模板ID',
    claimed_count INT             NOT NULL DEFAULT 0 COMMENT '已领取数量',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY pk_coupon_claim_counter (id),
    UNIQUE KEY uk_claim_counter_user_template (user_id, template_id),
    CONSTRAINT chk_claim_count CHECK (claimed_count >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '领券计数台账';
