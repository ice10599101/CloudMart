-- T19：任务奖励快照收口——先按当前配置一次性重建缺失/损坏快照，再落非空约束。
-- 回填口径与删除前的运行期回退完全一致（普通任务读 pet_daily_quest_config；
-- 宝箱优先读任务集生成时冻结的 chest_snapshot），因此不改变任何历史任务的经济结果。
-- 迁移后代码不再回退当前配置：缺快照即数据异常，必须显式暴露而不是静默改奖励。

-- 1) 普通任务：按配置重建快照（名称/口径/奖励/完成动作）
UPDATE pet_daily_quest q
JOIN pet_daily_quest_config c ON c.code = q.quest_code
SET q.reward_snapshot = JSON_OBJECT(
        'name', c.name,
        'questType', c.quest_type,
        'expReward', c.exp_reward,
        'currencyReward', c.currency_reward,
        'actionTarget', CASE c.quest_type
            WHEN 'FEED' THEN 'FEED'
            WHEN 'PLAY' THEN 'PLAY'
            WHEN 'CLEAN' THEN 'CLEAN'
            WHEN 'WORK' THEN 'WORK_START'
            WHEN 'STUDY' THEN 'STUDY_START'
            WHEN 'BOTTLE' THEN 'BOTTLE_START'
            WHEN 'CAREER_WORK' THEN 'CAREER_WORK_START'
            WHEN 'WALL_MESSAGE' THEN 'WALL_POST'
            WHEN 'REST' THEN 'REST_START'
            WHEN 'DECORATE' THEN 'HOME_DECORATE'
            WHEN 'VISIT' THEN 'VISIT_NEIGHBOR'
            WHEN 'COMPANION' THEN 'COMPANION_START'
            ELSE NULL END
    )
WHERE q.quest_code <> 'DAILY_CHEST'
  AND (q.reward_snapshot IS NULL
       OR JSON_VALID(q.reward_snapshot) = 0
       OR JSON_EXTRACT(q.reward_snapshot, '$.questType') IS NULL);

-- 2) 宝箱行：优先沿用任务集生成时冻结的宝箱快照；无集快照的历史行明确记零
UPDATE pet_daily_quest q
LEFT JOIN pet_daily_quest_set s ON s.id = q.set_id
SET q.reward_snapshot = CASE
        WHEN s.chest_snapshot IS NOT NULL
             AND JSON_VALID(s.chest_snapshot) = 1
             AND JSON_EXTRACT(s.chest_snapshot, '$.chestExp') IS NOT NULL
            THEN s.chest_snapshot
        ELSE JSON_OBJECT('chestExp', 0, 'chestCurrency', 0)
    END
WHERE q.quest_code = 'DAILY_CHEST'
  AND (q.reward_snapshot IS NULL
       OR JSON_VALID(q.reward_snapshot) = 0
       OR JSON_EXTRACT(q.reward_snapshot, '$.chestExp') IS NULL);

-- 3) 配置已删除且无集快照的普通任务：零奖励占位（此前回退同样取不到配置 → 0），
--    保留原始任务编码便于排查，不再由运行期猜测奖励
UPDATE pet_daily_quest
SET reward_snapshot = JSON_OBJECT(
        'name', quest_code,
        'expReward', 0,
        'currencyReward', 0,
        'actionTarget', NULL
    )
WHERE quest_code <> 'DAILY_CHEST'
  AND (reward_snapshot IS NULL OR JSON_VALID(reward_snapshot) = 0
       OR JSON_EXTRACT(reward_snapshot, '$.questType') IS NULL);

ALTER TABLE pet_daily_quest
    MODIFY `reward_snapshot` varchar(1000) NOT NULL
        COMMENT '生成时奖励快照 JSON（普通任务: name/questType/expReward/currencyReward/actionTarget；宝箱行: chestExp/chestCurrency）。T19 起非空，领取/展示只读快照';
