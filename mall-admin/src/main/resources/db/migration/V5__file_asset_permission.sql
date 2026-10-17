-- =============================================================================
-- V5__file_asset_permission.sql — ADMIN-04：管理员文件资产权限点
-- 文件管理菜单（2132）原无操作权限点；新增查询/删除两个 F 项，
-- 首期授予超级管理员，其余角色按最小权限原则在「角色管理」分配。
-- =============================================================================

INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(2133, '文件查询', 2132, 1, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'system:file:list', '#', 'ADMIN-04：管理员域资产分页/详情', '2026-10-17 00:00:00', '2026-10-17 00:00:00', NULL),
(2134, '文件删除', 2132, 2, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'system:file:delete', '#', 'ADMIN-04：管理员域资产删除（引用保护）', '2026-10-17 00:00:00', '2026-10-17 00:00:00', NULL);

INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 1, m.id, '2026-10-17 00:00:00', '2026-10-17 00:00:00'
FROM `admin_menu` m
WHERE m.id BETWEEN 2133 AND 2134
  AND NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.role_id = 1 AND r.menu_id = m.id);
