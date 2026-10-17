-- V10 修正：菜单删除清单误将 1010「角色管理」归类为部门按钮一并删除
-- （V1 实际：1006 部门管理，1007-1009 部门查询/新增/修改，1010 角色管理；
--   部门删除按钮为 1019，V10 又漏删）。本迁移恢复角色管理并补漏删清理。

-- 1. 恢复「角色管理」菜单（结构按 V1__baseline.sql 277 行重建，幂等）
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`)
SELECT 1010, '角色管理', 1000, 3, '/admin/system/roles', NULL, NULL, '', 1, 0, 'C', 1, 1, 'admin:role:list', 'peoples', NULL, NOW(3), NOW(3), NULL
WHERE NOT EXISTS (SELECT 1 FROM `admin_menu` WHERE `id` = 1010);

-- 2. 恢复授权：超级管理员(1) 与 系统管理员(91000005)，幂等
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 1, 1010, NOW(3), NOW(3)
WHERE NOT EXISTS (SELECT 1 FROM `admin_role_menu` WHERE `role_id` = 1 AND `menu_id` = 1010);
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 91000005, 1010, NOW(3), NOW(3)
WHERE NOT EXISTS (SELECT 1 FROM `admin_role_menu` WHERE `role_id` = 91000005 AND `menu_id` = 1010);

-- 3. 清理 V10 漏删的部门删除按钮（1019 属部门体系）
DELETE FROM `admin_role_menu` WHERE `menu_id` = 1019;
DELETE FROM `admin_menu` WHERE `id` = 1019;
