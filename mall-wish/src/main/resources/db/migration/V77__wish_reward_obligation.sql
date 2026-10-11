-- =============================================
-- V77: R07 跨服务奖励交付义务台账（wish→community 经验）
--   源事实（签到/里程碑领取行）与义务同事务落库（uk biz_key 每事实每受益人至多一次），
--   事务提交后即送（快路径）/ @Scheduled 补发（退避重试）——
--   修复"签到重试已签到导致经验永久漏发"与"提交前远端发奖、本地回滚留脏账"。
-- =============================================
CREATE TABLE IF NOT EXISTS wish_reward_obligation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_key VARCHAR(96) NOT NULL COMMENT '业务幂等键（来源事实+受益人，uk）',
    source VARCHAR(24) NOT NULL COMMENT '来源：WISH_SIGNIN/WISH_SIGNIN_MILESTONE',
    source_biz_id VARCHAR(64) NOT NULL COMMENT '来源事实 ID',
    beneficiary BIGINT UNSIGNED NOT NULL COMMENT '受益人用户 ID',
    reward_type VARCHAR(24) NOT NULL COMMENT 'EXP（community 成长经验）',
    amount INT NOT NULL COMMENT '数量',
    description VARCHAR(200) NULL COMMENT '发放描述（传 community）',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED/REJECTED',
    attempts INT NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    last_error VARCHAR(300) NULL COMMENT '最近错误（REJECTED 记拒绝原因）',
    next_retry_at DATETIME NULL COMMENT '下次重试时间（退避）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    settled_at DATETIME NULL COMMENT '交付完成时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_biz_key (biz_key),
    INDEX idx_status_retry (status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '跨服务奖励交付义务（R07）';
