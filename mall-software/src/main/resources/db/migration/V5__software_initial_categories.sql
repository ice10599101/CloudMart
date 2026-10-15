-- =============================================================================
-- V5__software_initial_categories.sql — 初始运营分类种子（方案 6.7）
-- 不为全体会员开通发布资格；不插入付款 grant 或虚构管理员联系方式。
-- =============================================================================

INSERT INTO `software_category` (`name`, `slug`, `sort_order`, `enabled`, `created_at`, `updated_at`) VALUES
('效率办公', 'productivity',    1, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('开发工具', 'developer-tools', 2, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('图像设计', 'design',          3, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('影音工具', 'media',           4, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('系统工具', 'system',          5, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('学习教育', 'education',       6, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('其他',     'others',          7, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
