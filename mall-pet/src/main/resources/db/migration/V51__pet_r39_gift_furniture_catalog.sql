-- V51 (R39): 新手/合作赠品家具补入受版本管理的目录
-- 原缺陷：代码赠送 starter_rug（新手礼）与 cooperation_badge（合作奖励），
-- 但家具目录无这两个 code——库存记录存在却无法 requireFurniture 摆放。
-- 幂等：uk_pet_furniture_code(code) 唯一键，重复执行/并行已存在时忽略。

INSERT IGNORE INTO `pet_furniture_config`
    (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`)
VALUES
    (9010101, 'starter_rug', '新手奶油地毯', '领养时收到的小地毯，奶油色的第一份家的味道。', 'FURNITURE', '🧶', 'COMMON', 0, 1, 1, 1, 20),
    (9010102, 'cooperation_badge', '合作纪念徽章', '和好友一起完成合作的纪念徽章。', 'FURNITURE', '🎖️', 'RARE', 0, 1, 2, 1, 21);
