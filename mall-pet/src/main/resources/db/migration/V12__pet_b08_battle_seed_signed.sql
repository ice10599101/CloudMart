-- V12: B08 对战 seed 迁移为有符号 BIGINT（Java long 可为负，UNSIGNED 列写入负数直接报错）
-- 迁移前先统计存量范围（结果写入 pet_migration_conflict，审计可查），确认无超界数据后改列类型；
-- 快照/回合 JSON 不受影响（种子只用于 Random 初始化，有符号域与 Java long 一致）。

-- 预检：记录当前 seed 极值（若有行）
INSERT INTO `pet_migration_conflict` (`conflict_type`, `entity_table`, `entity_id`, `user_id`, `detail`)
SELECT 'BATTLE_SEED_RANGE', 'pet_battle', 0, NULL,
       JSON_OBJECT('minSeed', MIN(b.`seed`), 'maxSeed', MAX(b.`seed`),
                   'rowCount', COUNT(*), 'action', 'MODIFY_TO_SIGNED_BIGINT')
FROM `pet_battle` b
HAVING COUNT(*) > 0;

ALTER TABLE `pet_battle`
    MODIFY COLUMN `seed` BIGINT NOT NULL COMMENT '战斗随机种子(结算前生成,有符号与Java long一致,保证离线复现)';
