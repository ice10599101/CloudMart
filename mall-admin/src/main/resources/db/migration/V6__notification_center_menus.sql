-- =============================================================================
-- V6__notification_center_menus.sql — ADMIN-03：通知菜单合一与批次查看权限
-- 1) 两旧通知入口合一：下线社区域「通知发送」菜单（4008/4158），保留业务域 2080 家族
--    （页面路由 /admin/community/notifications 同步删除）；
-- 2) 新增独立批次查看权限 business:notification:batch:list（与发送权限分离），
--    首期授予超级管理员，其余角色按需分配。
-- =============================================================================

DELETE FROM `admin_role_menu` WHERE `menu_id` IN (4008, 4158);
DELETE FROM `admin_menu` WHERE `id` IN (4008, 4158);

INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(2083, '群发批次查看', 2080, 3, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'business:notification:batch:list', '#', 'ADMIN-03：通知群发批次查看（与发送权限分离）', '2026-10-17 00:00:00', '2026-10-17 00:00:00', NULL);

INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 1, 2083, '2026-10-17 00:00:00', '2026-10-17 00:00:00'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.role_id = 1 AND r.menu_id = 2083);
