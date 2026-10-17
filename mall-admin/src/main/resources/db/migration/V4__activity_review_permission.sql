-- =============================================================================
-- V4__activity_review_permission.sql — ADMIN-05：活动报名审核权限点
-- 列表（business:activity:list）与审核（business:activity:review）分离：
-- 只读运营可查报名名单但不能审批。审核权限首期授予超级管理员，
-- 其余角色由运营在「角色管理」按需勾选，不在种子里扩大默认授权。
-- 菜单 ID 续用 3007 子项区间（3107~3109 已用，新增 3110）。
-- =============================================================================

INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(3110, '报名审核', 3007, 4, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'business:activity:review', '#', 'ADMIN-05：活动报名审核（与列表权限分离）', '2026-10-17 00:00:00', '2026-10-17 00:00:00', NULL);

INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 1, 3110, '2026-10-17 00:00:00', '2026-10-17 00:00:00'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.role_id = 1 AND r.menu_id = 3110);
