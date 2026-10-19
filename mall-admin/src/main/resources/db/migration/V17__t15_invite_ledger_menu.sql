-- T15：后台邀请台账——菜单"系统管理→邀请台账"（business:member 权限复用，与用户管理同目录；台账只读 + HOLD 复核释放）。
-- 菜单 ID 2066（2000 段空闲位；挂 1000 系统管理目录下）。
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(2066, '邀请台账', 1000, 9, '/admin/business/invites', 'admin/business/Invites', NULL, '', 1, 0, 'C', '0', '0', 'business:member:list', 'link', 'T15 邀请关系/资格状态/限制原因台账 + HOLD 复核释放（复核用 business:member:edit）', NOW(), NOW(), NULL);

-- 角色授权：超管（role_id=1）
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`)
SELECT 1, 2066, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `admin_role_menu` WHERE `role_id` = 1 AND `menu_id` = 2066);
