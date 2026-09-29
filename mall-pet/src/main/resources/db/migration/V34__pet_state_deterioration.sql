-- F4：宠物状态恶化机制——hunger=0 连续超 24h → WEAK（不可打工/对战/捞瓶）；
-- happiness=0 连续超 48h → SICK（经验获取减半）。两列记录"归零起点"（UTC），
-- 由 PetStateService 懒更新路径维护；恢复：饱食/心情回到 50+ 自动清除（读路径惰性结算）。
-- 宽限期：连续 7 天未登录（max_idle_hours 截断 48h 内）天然不会触发恶化，无需额外豁免逻辑。

ALTER TABLE `pet`
    ADD COLUMN `hunger_zero_since` DATETIME DEFAULT NULL
        COMMENT '饥饿归零起点(UTC)：hunger>0 置 NULL，=0 首次记录，用于 WEAK 判定' AFTER `hunger_frac`,
    ADD COLUMN `happiness_zero_since` DATETIME DEFAULT NULL
        COMMENT '心情归零起点(UTC)：happiness>0 置 NULL，=0 首次记录，用于 SICK 判定' AFTER `hunger_zero_since`;
