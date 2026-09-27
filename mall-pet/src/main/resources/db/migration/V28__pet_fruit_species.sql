-- V28: 宠物水果化 —— species 动物码 → 水果码（2026-09-28）
-- 项目处于开发阶段、无真实用户宠物：旧物种的宠物行直接删除（不做值迁移），枚举一步收窄。
-- 映射关系（前端 SPECIES_SLOT / FRUIT_SPECS 同源，仅供溯源）：
--   CAT→STRAWBERRY, DOG→ORANGE, RABBIT→WATERMELON, FOX→BLUEBERRY, PANDA→DRAGONFRUIT
-- 注：宠物的关联流水（钱包/聊天/背包等）为开发期孤儿数据，不在本迁移范围内，可另行清理。

-- 1) 旧物种宠物行整体删除（开发期数据，无需保留）
DELETE FROM `pet` WHERE `species` IN ('CAT','DOG','RABBIT','FOX','PANDA');

-- 2) 枚举一步收窄到水果码（此后 DB 枚举即为唯一白名单，越界值入库报错）
ALTER TABLE `pet`
    MODIFY `species`
    ENUM('STRAWBERRY','ORANGE','WATERMELON','BLUEBERRY','DRAGONFRUIT')
    NOT NULL COMMENT '种类:草莓/橘子/西瓜/蓝莓/火龙果';

-- 3) 皮肤配置：删除限定旧物种的在售皮肤（通用皮肤 species=NULL 不受影响）
DELETE FROM `pet_skin_config`
WHERE `species` IN ('CAT','DOG','RABBIT','FOX','PANDA');

-- 4) 皮肤限定种类注释对齐（VARCHAR 无枚举约束）
ALTER TABLE `pet_skin_config`
    MODIFY `species` VARCHAR(20) DEFAULT NULL
    COMMENT '限定种类(STRAWBERRY/ORANGE/WATERMELON/BLUEBERRY/DRAGONFRUIT, NULL=通用)';
