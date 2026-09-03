-- V24: 数据导出任务增加内容列（合规 34.2）
-- 导出的 JSON 内容直接落库（用户数据量级小），下载端点流式输出；
-- download_url 保留字段语义（未来迁移 OSS 时启用）
--
-- 幂等保护：MySQL DDL 非事务，迁移中断后 repair 仅清除 history 失败记录、
-- 不回滚已生效的 ALTER。曾出现本地库残留 content 列（schema 停在 23、
-- 列已存在、记录缺失），重放报 1060 Duplicate column 导致服务无法启动，
-- 故先探测列存在性再决定是否执行 ADD COLUMN。
SET @add_content_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'wish_data_export'
        AND COLUMN_NAME = 'content') = 0,
    'ALTER TABLE wish_data_export ADD COLUMN content LONGTEXT NULL COMMENT ''导出内容 JSON（SUCCESS 后写入，7 天过期清理）'' AFTER download_url',
    'SELECT 1'
);
PREPARE stmt FROM @add_content_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
