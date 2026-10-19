-- UX06：软件需求审核入口——菜单"软件管理→软件需求"（复用 software:moderate 审核权限）。
-- 菜单 ID 5243（5200 段空闲位）。
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(5243, '软件需求', 5200, 12, '/admin/software/requests', 'admin/software/Requests', NULL, '', 1, 0, 'C', '0', '0', 'software:moderate', 'bulb', 'UX06 找软件需求审核与"已找到"标记（software:moderate 权限复用）', NOW(), NOW(), NULL);

-- 角色授权：超管（role_id=1）
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`)
SELECT 1, m.id, NOW()
FROM `admin_menu` m
WHERE m.id = 5243
  AND NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.`role_id` = 1 AND r.`menu_id` = m.id);
