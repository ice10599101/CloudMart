-- =============================================
-- V8: 漂流瓶管理（重设计）菜单与权限点落库
-- 背景：mall-admin 补齐漂流瓶列表/详情/下架恢复/数据看板代理
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE
-- =============================================

-- 漂流瓶管理（C 行，path 对齐 .umirc /admin/business/drift-bottles）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3019, '漂流瓶管理', 2000, 40, '/admin/business/drift-bottles', 'C', 1, 1, 'business:driftBottle:list', 'message', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3138, '漂流瓶详情', 3019, 1, 'F', 1, 1, 'business:driftBottle:query', '#', NOW(), NOW()),
(3139, '漂流瓶下架恢复', 3019, 2, 'F', 1, 1, 'business:driftBottle:edit', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管绑定新增行
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id IN (3019, 3138, 3139) AND deleted_at IS NULL;
