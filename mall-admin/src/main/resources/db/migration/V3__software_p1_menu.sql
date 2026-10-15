-- =============================================================================
-- V3__software_p1_menu.sql — 小答软件 P1 菜单与权限种子（方案 10/7.5）
-- P1 菜单：专题管理（software:topic）、数据统计（software:stats）。
-- 菜单 ID 续用 5200 区间（V2 已用 5200~5215，此处 5216~5219）。
-- =============================================================================

INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(5216, '专题管理', 5200, 10, '/admin/software/topics',     NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:topic', 'collection', '专题合集与软件排序项整体维护（P1）', '2026-10-16 00:00:00', '2026-10-16 00:00:00', NULL),
(5217, '数据统计', 5200, 11, '/admin/software/statistics', NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:stats', 'chart',      '发布/审核时长/获取链接/收藏/评分/待办（P1）', '2026-10-16 00:00:00', '2026-10-16 00:00:00', NULL);

INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 1, m.id, '2026-10-16 00:00:00', '2026-10-16 00:00:00'
FROM `admin_menu` m
WHERE m.id BETWEEN 5216 AND 5219
  AND NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.role_id = 1 AND r.menu_id = m.id);
