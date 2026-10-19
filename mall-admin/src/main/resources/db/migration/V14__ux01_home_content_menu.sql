-- UX01：首页内容管理——菜单"软件管理→首页内容"（区块/条目/排序/预览）。
-- 菜单 ID 沿用 5200 段后续空闲位（当前最大 5218，本迁移用 5230/5231/5232）。
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(5230, '首页内容', 5200, 10, '/admin/software/home-content', 'admin/software/HomeContent', NULL, '', 1, 0, 'C', '0', '0', 'software:home:list', 'layout', 'UX01 首页固定区块配置（区块/条目/排序/投放期/预览）', NOW(), NOW(), NULL),
(5231, '首页内容管理', 5230, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'software:home:manage', '#', '区块与条目的增改/启停/排序', NOW(), NOW(), NULL),
(5232, '首页内容预览', 5230, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'software:home:preview', '#', '含未启用内容的预览读取', NOW(), NOW(), NULL);

-- 角色授权：超管（role_id=1）
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`)
SELECT 1, m.id, NOW()
FROM `admin_menu` m
WHERE m.id IN (5230, 5231, 5232)
  AND NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.`role_id` = 1 AND r.`menu_id` = m.id);
