-- PET-13/T32：相册绑定恢复——BINDING 行的自动重试状态机（方案 §7.2 数据模型：
-- bind_status/attempts/next_retry_at/last_error；远端引用键 PET_ALBUM:{id} 幂等，
-- 重试安全；超过上限转 FAILED 留人工/用户 retry-binding 处置）。

ALTER TABLE `pet_album_asset`
    ADD COLUMN `bind_attempts` INT NOT NULL DEFAULT 0 COMMENT '绑定已尝试次数（上传 1 次 + 自动/手动重试）' AFTER `bind_status`,
    ADD COLUMN `next_bind_retry_at` DATETIME NULL COMMENT '下次自动重试时间(UTC)；NULL 表示无待重试' AFTER `bind_attempts`,
    ADD COLUMN `last_bind_error` VARCHAR(255) NULL COMMENT '最近一次绑定失败原因（截断 255 字符）' AFTER `next_bind_retry_at`,
    ADD INDEX `idx_album_bind_retry` (`bind_status`, `next_bind_retry_at`, `id`);
