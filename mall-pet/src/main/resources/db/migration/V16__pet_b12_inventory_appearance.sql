-- V16: B12 背包/外观 schema
-- 1) 原始自定义外观（穿戴皮肤前保存，卸皮肤恢复原值而非物种默认）
ALTER TABLE `pet`
    ADD COLUMN `base_appearance` VARCHAR(255) DEFAULT NULL
        COMMENT '原始自定义外观JSON(穿戴皮肤前保存;卸皮肤恢复;NULL=从无自定义外观,采用物种默认)' AFTER `appearance`;

-- 2) 同槽并发唯一约束（B12：并发穿戴同槽仅一件生效，数据库兜底）
ALTER TABLE `pet_inventory`
    ADD UNIQUE KEY `uk_inventory_slot_equipped` (`pet_id`, `slot`,
        (IF(`item_type` = 'EQUIPMENT' AND `equipped` = 1, 1, NULL))),
    ADD UNIQUE KEY `uk_inventory_skin_equipped` (`pet_id`,
        (IF(`item_type` = 'SKIN' AND `equipped` = 1, 1, NULL)));
