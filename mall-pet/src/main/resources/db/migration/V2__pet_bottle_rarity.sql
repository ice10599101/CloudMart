-- CloudMart 社区宠物模块 V2：稀有瓶/彩蛋瓶/宠物瓶 + 捞瓶区域（原文档 §19/§20）
-- 捞瓶奖励类型：普通瓶=真实社区漂流瓶（bottle_id 指向 mall_wish）；
-- 稀有瓶/彩蛋瓶/宠物瓶=服务端生成的特殊内容（special_content），不建第二套漂流瓶

ALTER TABLE `pet_bottle_record`
    ADD COLUMN `rarity` ENUM('NORMAL','RARE','PET','EASTER_EGG') NOT NULL DEFAULT 'NORMAL' COMMENT '瓶子稀有度:普通/稀有/宠物瓶/彩蛋瓶(特殊瓶不消耗mall-wish瓶子池)' AFTER `outcome`,
    ADD COLUMN `special_content` VARCHAR(500) DEFAULT NULL COMMENT '特殊瓶子内容文本(稀有瓶/宠物瓶/彩蛋瓶专属,普通瓶为空)' AFTER `rarity`,
    ADD INDEX `idx_bottle_record_rarity` (`pet_id`, `rarity`);
