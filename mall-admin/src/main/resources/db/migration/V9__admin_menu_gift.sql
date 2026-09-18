-- =============================================
-- V9: 礼物管理菜单与权限点落库（全站虚拟礼物）
-- 背景：mall-admin 补齐礼物目录 CRUD/上下架/删除/送礼记录代理
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE
-- 对应前端路由：/admin/business/gifts
-- =============================================

-- 礼物管理（C 行，挂在业务管理 2000 下，path 对齐 .umirc /admin/business/gifts）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3020, '礼物管理', 2000, 41, '/admin/business/gifts', 'C', 1, 1, 'business:gift:list', 'gift', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 按钮权限（后端校验 business:gift:add/edit/delete；记录查询与目录同权限点 business:gift:list）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3140, '礼物新增', 3020, 1, 'F', 1, 1, 'business:gift:add',    '#', NOW(), NOW()),
(3141, '礼物编辑', 3020, 2, 'F', 1, 1, 'business:gift:edit',   '#', NOW(), NOW()),
(3142, '礼物删除', 3020, 3, 'F', 1, 1, 'business:gift:delete', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管绑定新增行
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id IN (3020, 3140, 3141, 3142) AND deleted_at IS NULL;
