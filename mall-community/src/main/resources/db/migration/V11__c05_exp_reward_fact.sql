-- C05：经验奖励事实唯一——同 (user, source, biz_id) 至多一条奖励日志，
-- 经验加值与奖励事实一一对应，防并发/重试双发奖（签到 biz_id=checkIn.id 天然唯一）。
ALTER TABLE `exp_logs`
    ADD UNIQUE KEY `uk_explog_reward_fact` (`user_id`, `source`, `biz_id`);
