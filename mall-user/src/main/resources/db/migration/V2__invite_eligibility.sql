-- WISH-04：邀请奖励资格与经济规则收口
-- 关系台账加资格/奖励状态机与限制原因；达到资格仅发一次（GRANTED 幂等）。
ALTER TABLE `user_invite_relation`
    ADD COLUMN `reward_status` varchar(24) NOT NULL DEFAULT 'PENDING_ELIGIBILITY'
        COMMENT '奖励状态: PENDING_ELIGIBILITY/ELIGIBLE/HOLD/REJECTED_LIMIT/GRANTED (WISH-04)' AFTER `invitee_id`,
    ADD COLUMN `reward_reason` varchar(300) DEFAULT NULL
        COMMENT '限制/拒绝原因（后台可查, WISH-04）' AFTER `reward_status`,
    ADD COLUMN `eligible_at` datetime DEFAULT NULL
        COMMENT '达到资格时间（2 个不同业务日签到+账号满 24h, WISH-04）' AFTER `reward_reason`,
    ADD COLUMN `rule_version` int NOT NULL DEFAULT '1'
        COMMENT '邀请规则版本（页面展示, WISH-04）' AFTER `eligible_at`,
    ADD KEY `idx_invite_inviter_reward` (`inviter_id`, `reward_status`);
