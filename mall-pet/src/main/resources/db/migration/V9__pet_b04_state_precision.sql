-- V9: B04 自然状态变化精度——为每个连续变化属性保存小数余量
-- 现状：每小时变化量按整点向下取整后推进游标，高频查询（每分钟一次）会丢弃全部小数变化，
--      默认饱食 2/小时 时饿死在半路；且一个属性的步长取整不影响其他属性余量（独立列）。
-- 方案：余量独立列（DECIMAL 有符号小数），结算时 total = 余量 + 时长*速率，整数部分入属性、小数回写余量；
--      主动互动直接改属性值时余量清零（由服务层处理），避免"储备变化"突然释放。

ALTER TABLE `pet`
    ADD COLUMN `hunger_frac` DECIMAL(10, 4) NOT NULL DEFAULT 0 COMMENT '饱食变化小数余量(独立累计,不吞步长)' AFTER `cleanliness`,
    ADD COLUMN `happiness_frac` DECIMAL(10, 4) NOT NULL DEFAULT 0 COMMENT '心情变化小数余量(独立累计)' AFTER `hunger_frac`,
    ADD COLUMN `energy_frac` DECIMAL(10, 4) NOT NULL DEFAULT 0 COMMENT '精力变化小数余量(独立累计)' AFTER `happiness_frac`,
    ADD COLUMN `cleanliness_frac` DECIMAL(10, 4) NOT NULL DEFAULT 0 COMMENT '清洁变化小数余量(独立累计)' AFTER `energy_frac`;
