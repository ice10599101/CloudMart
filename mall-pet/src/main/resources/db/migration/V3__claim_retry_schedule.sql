-- T18：通行证领取补发队列——公平扫描、指数退避、租约并发领取、失败处置。
-- 无数据迁移风险；仅新增可观测/调度字段。状态仍为 varchar（NEEDS_ATTENTION 复用现列）。
ALTER TABLE `pet_season_pass_claim`
    ADD COLUMN `attempts` int NOT NULL DEFAULT 0 COMMENT '交付尝试次数（失败累计，成功不重置）',
    ADD COLUMN `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间（UTC；NULL=立即可执行）',
    ADD COLUMN `last_error_code` varchar(40) DEFAULT NULL COMMENT '最近失败错误码（用于可重试/不可重试分类与告警）',
    ADD COLUMN `lease_owner` varchar(64) DEFAULT NULL COMMENT '交付租约持有者（实例 ID；NULL=空闲）',
    ADD COLUMN `lease_until` datetime DEFAULT NULL COMMENT '租约到期时间（UTC）',
    ADD COLUMN `last_attempt_at` datetime DEFAULT NULL COMMENT '最近一次尝试时间（UTC）';

-- 公平扫描索引：按「已到期且可执行」过滤（status + next_retry_at）
ALTER TABLE `pet_season_pass_claim`
    ADD KEY `idx_claim_due` (`status`, `next_retry_at`);
