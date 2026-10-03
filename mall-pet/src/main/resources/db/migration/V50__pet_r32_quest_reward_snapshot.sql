-- V50 (R32): 每日任务奖励快照——领取/展示读快照，运营改配置不改变已生成任务的经济结果
-- 原缺陷：任务行只存 targetValue，claim/buildVo 读当前配置奖励，宝箱奖励实时读
-- properties——进行中任务的经济结果随运营编辑漂移（违反 §5.1 不变量 5）。

ALTER TABLE `pet_daily_quest`
    ADD COLUMN `reward_snapshot` VARCHAR(1000) DEFAULT NULL
        COMMENT '生成时奖励快照 JSON（普通任务: name/questType/expReward/currencyReward/actionTarget；宝箱行: chestExp/chestCurrency）。NULL=存量行回退当前配置' AFTER `target_value`;
