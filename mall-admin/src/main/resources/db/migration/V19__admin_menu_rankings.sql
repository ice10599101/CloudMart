-- =============================================
-- V19: 社区排行榜赛季页菜单（T23/P0-4 顺带项）
-- 背景：mall-admin 已代理 /admin/community/rankings/*（community:ranking:read/manage），
--       CloudMart-ui 新增 /admin/community/rankings 页面，本迁移补菜单与权限点。
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE（对齐 V10/V14 模式）
-- =============================================

-- 页面菜单（C 行）：读接口全部要求 community:ranking:read，故 C 行 perms 与之对齐，
-- 未授权角色侧边栏不可见
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(4010, '排行榜赛季', 4000, 10, '/admin/community/rankings', 'C', 1, 1, 'community:ranking:read', 'crown', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 操作按钮（F 行）：赛季启停/归档为写操作（community:ranking:manage）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(4019, '赛季归档启停', 4010, 1, 'F', 1, 1, 'community:ranking:manage', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管角色（role_id=1）自动可见；自定义角色由菜单管理按需授予
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE deleted_at IS NULL AND id IN (4010, 4019);
