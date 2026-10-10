-- =============================================
-- V64: 语音漂流瓶（§6 语音漂流瓶）：content / audioUrl / wishId 三选一（表名 wish_drift_bottle）
--   - audio_url：S01 资产通道音频（/files/... 前缀，服务端校验）
--   - audio_duration_seconds：客户端声明时长（服务端上限 60s 校验）
-- 守卫：表存在 + 列缺失才 ALTER（本地/服务器库状态漂移均可安全重放）
-- =============================================
SET @t_exists := (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish_drift_bottle');
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish_drift_bottle' AND COLUMN_NAME = 'audio_url');
SET @ddl := IF(@t_exists = 1 AND @c1 = 0,
    'ALTER TABLE wish_drift_bottle ADD COLUMN audio_url VARCHAR(500) NULL COMMENT ''语音瓶音频 URL（S01 资产 /files/ 前缀；与 content/wishId 三选一）''',
    'SELECT ''跳过（表不存在或 audio_url 已存在（wish_drift_bottle））'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish_drift_bottle' AND COLUMN_NAME = 'audio_duration_seconds');
SET @ddl := IF(@t_exists = 1 AND @c2 = 0,
    'ALTER TABLE wish_drift_bottle ADD COLUMN audio_duration_seconds INT NULL COMMENT ''语音时长秒（≤60，服务端校验）''',
    'SELECT ''跳过（表不存在或 audio_duration_seconds 已存在（wish_drift_bottle））'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
