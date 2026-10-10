-- =============================================
-- V64: 语音漂流瓶（§6 语音漂流瓶）：content / audioUrl / wishId 三选一
--   - audio_url：S01 资产通道音频（/files/... 前缀，服务端校验）
--   - audio_duration_seconds：客户端声明时长（服务端上限 60s 校验）
-- =============================================
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'drift_bottle' AND COLUMN_NAME = 'audio_url');
SET @ddl := IF(@c1 = 0,
    'ALTER TABLE drift_bottle ADD COLUMN audio_url VARCHAR(500) NULL COMMENT ''语音瓶音频 URL（S01 资产 /files/ 前缀；与 content/wishId 三选一）''',
    'SELECT ''drift_bottle.audio_url 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'drift_bottle' AND COLUMN_NAME = 'audio_duration_seconds');
SET @ddl := IF(@c2 = 0,
    'ALTER TABLE drift_bottle ADD COLUMN audio_duration_seconds INT NULL COMMENT ''语音时长秒（≤60，服务端校验）''',
    'SELECT ''audio_duration_seconds 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
