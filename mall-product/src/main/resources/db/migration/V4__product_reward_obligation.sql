-- =============================================
-- V4: R07 评价奖励交付义务台账（mall-product→mall-wish 星光）
--   评价行与义务同事务落库（uk biz_key 每评价至多一次），提交后即送/失败退避补发——
--   修复"afterCommit 即时调用失败只留日志，进程崩溃后永久漏发"。
-- =============================================
CREATE TABLE IF NOT EXISTS product_reward_obligation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_key VARCHAR(96) NOT NULL COMMENT '业务幂等键（REVIEW_REWARD:{reviewId}，uk）',
    source VARCHAR(24) NOT NULL COMMENT 'REVIEW_REWARD',
    source_biz_id VARCHAR(64) NOT NULL COMMENT '来源事实 ID（评价 ID）',
    beneficiary BIGINT UNSIGNED NOT NULL COMMENT '受益人用户 ID',
    reward_type VARCHAR(24) NOT NULL COMMENT 'STARLIGHT',
    amount INT NOT NULL COMMENT '数量',
    description VARCHAR(200) NULL COMMENT '备注',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED/REJECTED',
    attempts INT NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    last_error VARCHAR(300) NULL COMMENT '最近错误',
    next_retry_at DATETIME NULL COMMENT '下次重试时间（退避）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    settled_at DATETIME NULL COMMENT '交付完成时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_biz_key (biz_key),
    INDEX idx_status_retry (status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '评价奖励交付义务（R07）';
