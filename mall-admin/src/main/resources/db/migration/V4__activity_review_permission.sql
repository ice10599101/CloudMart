-- =============================================================================
-- V4__activity_review_permission.sql — ADMIN-05：活动报名审核权限点
-- 列表（business:activity:list）与审核（business:activity:review）分离：
-- 只读运营可查报名名单但不能审批。审核权限首期授予超级管理员，
-- 其余角色由运营在「角色管理」按需勾选，不在种子里扩大默认授权。
-- 菜单 ID 用 3125（远程库 3110~3124 已被匹配/排行榜/Prompt 等菜单占用，PRODUCT-01 修复）。
-- =============================================================================

INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(3125, '报名审核', 3007, 4, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'business:activity:review', '#', 'ADMIN-05：活动报名审核（与列表权限分离）', NOW(), NOW(), NULL);

INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 1, 3125, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.role_id = 1 AND r.menu_id = 3125);
