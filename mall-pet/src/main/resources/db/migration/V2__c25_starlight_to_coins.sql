-- =====================================================================
-- C25：宠物经济字段去星光命名——价格/奖励/消耗统一为 Coins（宠物币 PET_COIN）。
-- 仅列名与注释变更，类型/默认值/约束不变；历史期次冻结快照 JSON 内的旧键
-- （rewardStarlight/rewardAltStarlight）由应用层逐字段回退配置读取，不迁数据。
-- =====================================================================

ALTER TABLE `pet_equipment_config`
    CHANGE COLUMN `price_starlight` `price_coins` int NOT NULL DEFAULT '0' COMMENT '售价(宠物币)';
ALTER TABLE `pet_skin_config`
    CHANGE COLUMN `price_starlight` `price_coins` int NOT NULL DEFAULT '0' COMMENT '售价(宠物币)';
ALTER TABLE `pet_skill_config`
    CHANGE COLUMN `price_starlight` `price_coins` int NOT NULL DEFAULT '0' COMMENT '售价(宠物币,购买后落背包技能书)';
ALTER TABLE `pet_food_config`
    CHANGE COLUMN `price_starlight` `price_coins` int NOT NULL DEFAULT '0' COMMENT '售价(宠物币)';
ALTER TABLE `pet_furniture_config`
    CHANGE COLUMN `price_starlight` `price_coins` int NOT NULL DEFAULT '0' COMMENT '售价(宠物币)';
ALTER TABLE `pet_evolution_config`
    CHANGE COLUMN `cost_starlight` `cost_coins` int NOT NULL DEFAULT '0' COMMENT '进化消耗宠物币';
ALTER TABLE `pet_event_config`
    CHANGE COLUMN `reward_starlight` `reward_coins` int NOT NULL DEFAULT '0' COMMENT '奖励宠物币',
    CHANGE COLUMN `reward_alt_starlight` `reward_alt_coins` int NOT NULL DEFAULT '0' COMMENT '替代奖励宠物币';
ALTER TABLE `pet_season_reward`
    CHANGE COLUMN `reward_starlight` `reward_coins` int NOT NULL DEFAULT '0' COMMENT '赛季奖励宠物币';
