-- TRADE-02：盘点调整单权限——与初始化/查询分离（可授予仓库/运营角色的最小权限点）
-- 菜单 ID 续用 2060 子项区间（2061/2062 已用，新增 2063）。
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(2063, '库存调整', 2060, 3, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'business:inventory:adjust', '#', 'TRADE-02：盘点调整单（delta/原因/版本CAS/幂等键，全量审计）', '2026-10-17 00:00:00', '2026-10-17 00:00:00', NULL);

INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 1, 2063, '2026-10-17 00:00:00', '2026-10-17 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.role_id = 1 AND r.menu_id = 2063);
