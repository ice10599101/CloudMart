-- =============================================
-- V5: 心愿虚拟资产管理 + 品牌入驻审核 权限点落库
-- 背景：mall-admin 补齐虚拟资产/品牌审核管理代理（Sprint 3.6 管理后台收尾）
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE
-- =============================================

-- 心愿资产管理（C 行，path 对齐 .umirc /admin/business/wish-assets）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3018, '心愿资产管理', 2000, 39, '/admin/business/wish-assets', 'C', 1, 1, 'business:asset:list', 'gift', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3135, '资产编辑', 3018, 1, 'F', 1, 1, 'business:asset:edit', '#', NOW(), NOW()),
(3136, '品牌审核', 3018, 2, 'F', 1, 1, 'business:wishBrand:audit', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管绑定新增行
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id IN (3018, 3135, 3136) AND deleted_at IS NULL;
