-- V39: 宠物代捞漂流瓶的幂等请求标识（B11）
-- 背景：远程打捞超时重试可能二次抢瓶；定时任务线程也没有登录头。
-- 方案：打捞流水增加 request_id（宠物模块按捞瓶活动生成的稳定请求标识，uk 唯一）——
--      相同 request_id 重入直接返回原瓶子结果，不二次抢瓶、不二次计配额。

ALTER TABLE `wish_drift_bottle_fish_log`
    ADD COLUMN `request_id` VARCHAR(64) DEFAULT NULL COMMENT '业务请求标识(宠物代捞按活动生成,幂等重放依据)' AFTER `user_id`;

ALTER TABLE `wish_drift_bottle_fish_log`
    ADD UNIQUE KEY `uk_fish_log_request` (`request_id`);
