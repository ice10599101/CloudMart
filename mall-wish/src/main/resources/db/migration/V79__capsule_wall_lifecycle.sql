-- =============================================
-- V79: 公共胶囊墙生命周期补列（R17）
--   wall_status 扩展：0 未申请 / 1 待审核 / 2 已上墙 / 3 已拒绝 / 4 已撤下（owner 撤回申请/主动撤下、管理员下架共用终态，可重新申请）
--   新增 wall_reason：驳回理由 / 下架理由（作者端可见）
--   新增 wall_actor_id：最后一次墙裁决操作者（管理员 ID；owner 自助操作为用户本人 ID）
-- 守卫：表存在 + 列缺失才 ALTER（漂移库安全重放，对齐 V65/V66 模式）
-- =============================================
SET @t_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'time_capsule');

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'time_capsule' AND COLUMN_NAME = 'wall_reason');
SET @ddl := IF(@t_exists = 1 AND @c1 = 0,
    'ALTER TABLE time_capsule ADD COLUMN wall_reason VARCHAR(200) NULL COMMENT ''公共胶囊墙驳回/下架理由（作者可见）''',
    'SELECT ''跳过（表不存在或 wall_reason 已存在）'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'time_capsule' AND COLUMN_NAME = 'wall_actor_id');
SET @ddl := IF(@t_exists = 1 AND @c2 = 0,
    'ALTER TABLE time_capsule ADD COLUMN wall_actor_id BIGINT UNSIGNED NULL COMMENT ''最后一次墙裁决操作者ID（管理员/作者本人）''',
    'SELECT ''跳过（表不存在或 wall_actor_id 已存在）'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
