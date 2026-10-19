-- =============================================================================
-- V13__sf08_review_claim_history_copy.sql — SF08 审核分配与历史恢复
-- 1) claimed_by/claimed_at：审核领取协作标记（无领取版本号——最终决定仍以
--    服务端状态原子执行）；领取超时由判定时刻惰性计算，不新增调度。
-- 2) copied_from_revision_id：历史恢复复制的来源标记（幂等判定与追溯）。
-- =============================================================================

ALTER TABLE `software_revision`
    ADD COLUMN `claimed_by` bigint unsigned DEFAULT NULL
        COMMENT '审核领取人（协作标记；最终决定以服务端原子审核为准）' AFTER `review_reason`,
    ADD COLUMN `claimed_at` datetime(3) DEFAULT NULL
        COMMENT '领取时间（超时惰性释放判定依据）' AFTER `claimed_by`,
    ADD COLUMN `copied_from_revision_id` bigint unsigned DEFAULT NULL
        COMMENT '历史恢复：来源快照ID（DRAFT 复制自哪次历史提交）' AFTER `claimed_at`;

CREATE INDEX `idx_revision_claim`
    ON `software_revision` (`review_status`, `claimed_by`, `claimed_at`);
