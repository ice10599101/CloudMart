-- =============================================================================
-- V2__software_admin_menu.sql — 小答软件后台一级菜单与权限种子（方案 10 / 7.5）
-- P0 菜单：软件管理、发布审核、分类与标签、评论管理、举报管理、链接反馈、
--          发布资格、付款查看授权、联系入口（专题/统计为 P1，后续迁移追加）。
-- 权限拆分（10）：查询/审核/处置/来源/评论/分类 + 独立权限域
--   publisher:list|manage、access:list|manage、contact:manage、permission:audit；
-- 角色种子只绑定超级管理员（role_id=1），不默认授予内容审核员。
-- 菜单 ID 区间 5200~5215（V1 基线已占用 4000~4158、5000~5004）。
-- =============================================================================

-- 一级目录
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(5200, '小答软件', 0, 12, '/admin/software', NULL, NULL, '', 1, 0, 'M', 1, 1, NULL, 'appstore', '站内软件分享与交流模块', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL);

-- 二级菜单（C：查询可见性由菜单权限控制）
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(5201, '软件管理',     5200, 1, '/admin/software/items',         NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:list',             'file-search', '软件目录/精选/封禁处置', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5202, '发布审核',     5200, 2, '/admin/software/reviews',       NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:review',           'audit',       '待审快照与通过/退回决策', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5203, '分类与标签',   5200, 3, '/admin/software/categories',    NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:category',         'tag',         '分类标签维护，DELETE=禁用', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5204, '评论管理',     5200, 4, '/admin/software/comments',      NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:comment',          'comment',     '待审/可见/隐藏', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5205, '举报管理',     5200, 5, '/admin/software/reports',       NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:report',           'alert',       '软件举报闭环（software_report）', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5206, '链接反馈',     5200, 6, '/admin/software/link-feedback', NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:link',             'link',        '失效反馈核实与来源禁用', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5207, '发布资格',     5200, 7, '/admin/software/publishers',    NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:publisher:list',   'user',        '逐用户开通/关闭投稿资格', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5208, '付款查看授权', 5200, 8, '/admin/software/access-grants', NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:access:list',      'safety',      '管理员线下确认后人工开通/续期/撤销', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5209, '联系入口',     5200, 9, '/admin/software/access-contacts', NULL, NULL, '', 1, 0, 'C', 1, 1, 'software:contact:manage', 'phone',       '付款查看对外联系方式维护', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL);

-- 按钮级权限（F：管理动作与查询分离；不默认授予内容审核员）
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(5210, '软件封禁处置', 5201, 1, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'software:moderate', '#', '平台封禁/解除（解除回到 UNLISTED）', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5211, '精选推荐',     5201, 2, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'software:feature',  '#', '首页精选排序 featured_rank', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5212, '来源状态处置', 5206, 1, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'software:link',     '#', '核实来源/禁用/解除（software:link 复用菜单权限）', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5213, '发布资格开关', 5207, 1, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'software:publisher:manage', '#', '开通/关闭投稿资格（带原因；服务端行锁）', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5214, '授权管理',     5208, 1, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'software:access:manage', '#', '人工开通/续期/撤销付款查看授权', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL),
(5215, '权限审计查询', 5208, 2, '', NULL, NULL, '', 1, 0, 'F', 1, 1, 'software:permission:audit', '#', '发布资格与链接授权操作历史', '2026-10-15 00:00:00', '2026-10-15 00:00:00', NULL);

-- 超级管理员绑定全部新菜单（其余角色由运营按权限域自行分配）
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`, `updated_at`)
SELECT 1, m.id, '2026-10-15 00:00:00', '2026-10-15 00:00:00'
FROM `admin_menu` m
WHERE m.id BETWEEN 5200 AND 5215
  AND NOT EXISTS (SELECT 1 FROM `admin_role_menu` r WHERE r.role_id = 1 AND r.menu_id = m.id);
