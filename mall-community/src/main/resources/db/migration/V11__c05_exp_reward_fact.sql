-- C05：经验奖励事实唯一——同 (user_id, source, biz_id) 至多一条奖励日志。
-- 存量数据存在重复奖励行（并发/重试双发奖的证据），先收敛：保留每组最早一条（id 最小），
-- 其余删除；biz_id 为 NULL 的行不构成奖励事实，不参与去重（唯一键允许多个 NULL）。
DELETE e1 FROM `exp_logs` e1
JOIN `exp_logs` e2
  ON  e1.user_id = e2.user_id
 AND  e1.source  = e2.source
 AND  e1.biz_id  = e2.biz_id
 AND  e1.id > e2.id
WHERE e1.biz_id IS NOT NULL;

ALTER TABLE `exp_logs`
    ADD UNIQUE KEY `uk_explog_reward_fact` (`user_id`, `source`, `biz_id`);
