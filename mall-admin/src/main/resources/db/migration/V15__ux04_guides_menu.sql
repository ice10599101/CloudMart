-- UX04：软件教程管理——菜单"软件管理→软件教程"（元数据维护；正文在社区）。
-- 菜单 ID 段 5240~5242（5200 段后续空闲位）。
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(5240, '软件教程', 5200, 11, '/admin/software/guides', 'admin/software/Guides', NULL, '', 1, 0, 'C', '0', '0', 'software:guide:list', 'documentation', 'UX04 教程元数据管理（正文与审核真源在社区）', NOW(), NOW(), NULL),
(5241, '教程管理', 5240, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'software:guide:manage', '#', '教程元数据增改/启停/删除（articleId 绑定社区文章）', NOW(), NOW(), NULL),
(5242, '教程预览', 5240, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'software:guide:review', '#', '教程内容适用性检查入口', NOW(), NOW(), NULL);

-- 角色授权：超管（role_id=1）
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`)
SELECT 1, m.id, NOW()
FROM `admin_menu` m
WHERE m.id IN (5240, 5241, 5242)
  AND NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.`role_id` = 1 AND r.`menu_id` = m.id);
