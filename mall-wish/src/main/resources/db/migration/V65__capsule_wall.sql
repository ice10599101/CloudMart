-- =============================================
-- V65: 公共胶囊墙（§6 公共胶囊墙）：匿名精选墙 + 审核
--   - wall_status：0 未申请 / 1 待审核 / 2 已上墙 / 3 已拒绝（仅 OPENED 胶囊可申请）
--   - wall_applied_at / wall_decided_at：审计时间
--   管理端审核列表 + 通过/拒绝（AdminCapsuleController 扩展）；公开墙仅 status=2 匿名展示
-- 守卫：表存在 + 列缺失才 ALTER（本地/服务器库状态漂移均可安全重放）
-- =============================================
SET @t_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'time_capsule');
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'time_capsule' AND COLUMN_NAME = 'wall_status');
SET @ddl := IF(@t_exists = 1 AND @c1 = 0,
    'ALTER TABLE time_capsule ADD COLUMN wall_status TINYINT NOT NULL DEFAULT 0 COMMENT ''公共胶囊墙：0 未申请 / 1 待审核 / 2 已上墙 / 3 已拒绝''',
    'SELECT ''跳过（表不存在或 wall_status 已存在）'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'time_capsule' AND COLUMN_NAME = 'wall_applied_at');
SET @ddl := IF(@t_exists = 1 AND @c2 = 0,
    'ALTER TABLE time_capsule ADD COLUMN wall_applied_at DATETIME NULL COMMENT ''申请上墙时间''',
    'SELECT ''跳过（表不存在或 wall_applied_at 已存在）'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'time_capsule' AND COLUMN_NAME = 'wall_decided_at');
SET @ddl := IF(@t_exists = 1 AND @c3 = 0,
    'ALTER TABLE time_capsule ADD COLUMN wall_decided_at DATETIME NULL COMMENT ''审核决定时间''',
    'SELECT ''跳过（表不存在或 wall_decided_at 已存在）'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'time_capsule' AND INDEX_NAME = 'idx_wall_status');
SET @ddl := IF(@t_exists = 1 AND @idx = 0,
    'CREATE INDEX idx_wall_status ON time_capsule (wall_status, wall_decided_at)',
    'SELECT ''跳过（表不存在或 idx_wall_status 已存在）'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
