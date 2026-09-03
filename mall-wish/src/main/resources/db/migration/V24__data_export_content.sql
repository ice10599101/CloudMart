-- V6: 数据导出任务增加内容列（合规 34.2）
-- 导出的 JSON 内容直接落库（用户数据量级小），下载端点流式输出；
-- download_url 保留字段语义（未来迁移 OSS 时启用）
ALTER TABLE wish_data_export
    ADD COLUMN content LONGTEXT NULL COMMENT '导出内容 JSON（SUCCESS 后写入，7 天过期清理）' AFTER download_url;
