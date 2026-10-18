-- =============================================================================
-- V13__software_fanout_ops_menu.sql — SW10 通知任务运维按钮权限
-- 通知与检查任务（fanout）运维入口挂在"数据统计"（5217）下，权限独立：
-- software:fanout；只默认授予超级管理员（role_id=1）。
-- =============================================================================

INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(5218, '通知任务运维', 5217, 1, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'software:fanout', '#', '通知任务列表与人工重试（沿用原发布事件身份）', NOW(3), NOW(3), NULL);

INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 1, m.id, NOW(3), NOW(3)
FROM `admin_menu` m
WHERE m.id = 5218
  AND NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.role_id = 1 AND r.menu_id = m.id);
