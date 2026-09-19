-- =============================================
-- V10: 宠物运营菜单与权限点落库（社区宠物模块三期）
-- 背景：mall-admin 新增宠物配置管理/留言审核/数据看板代理（/admin/pet/**）
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE
-- =============================================

-- 宠物运营（C 行，path 对齐 .umirc /admin/business/pet）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3020, '宠物运营', 2000, 41, '/admin/business/pet', 'C', 1, 1, 'business:pet:list', 'heart', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3140, '宠物查询', 3020, 1, 'F', 1, 1, 'business:pet:list', '#', NOW(), NOW()),
(3141, '宠物配置编辑', 3020, 2, 'F', 1, 1, 'business:pet:edit', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管绑定新增行
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id IN (3020, 3140, 3141) AND deleted_at IS NULL;
