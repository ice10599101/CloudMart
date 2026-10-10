-- =============================================
-- V66: 语音漂流瓶补列（V64 表名笔误修正）：对 wish_drift_bottle 加音频列
--   V64 因表名误写 drift_bottle（实际 wish_drift_bottle）被守卫跳过且
--   已标记 success——本迁移以正确表名补齐，双守卫幂等。
-- =============================================
SET @t_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish_drift_bottle');
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish_drift_bottle' AND COLUMN_NAME = 'audio_url');
SET @ddl := IF(@t_exists = 1 AND @c1 = 0,
    'ALTER TABLE wish_drift_bottle ADD COLUMN audio_url VARCHAR(500) NULL COMMENT ''语音瓶音频 URL（S01 资产 /files/ 前缀；与 content/wishId 三选一）''',
    'SELECT ''跳过（表不存在或 audio_url 已存在）'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish_drift_bottle' AND COLUMN_NAME = 'audio_duration_seconds');
SET @ddl := IF(@t_exists = 1 AND @c2 = 0,
    'ALTER TABLE wish_drift_bottle ADD COLUMN audio_duration_seconds INT NULL COMMENT ''语音时长秒（≤60，服务端校验）''',
    'SELECT ''跳过（表不存在或 audio_duration_seconds 已存在）'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
