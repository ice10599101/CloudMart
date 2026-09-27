-- =============================================
-- V11: 宠物钱包管理菜单与权限点落库（W04，§4.1）
-- 对齐：business:pet:wallet:read / wallet:adjust:request / wallet:adjust:approve
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE
-- 注：既有配置运营员如需只读权限，由运营按最小权限另行授权（本迁移仅绑超管）
-- =============================================

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3220, '宠物钱包', 3020, 5, '/admin/business/pet-wallet', 'C', 1, 1, 'business:pet:wallet:read', 'wallet', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3221, '钱包查询',     3220, 1, 'F', 1, 1, 'business:pet:wallet:read',            '#', NOW(), NOW()),
(3222, '流水查询',     3220, 2, 'F', 1, 1, 'business:pet:wallet:read',            '#', NOW(), NOW()),
(3223, '冻结/解冻',    3220, 3, 'F', 1, 1, 'business:pet:wallet:adjust:approve',  '#', NOW(), NOW()),
(3224, '调账申请',     3220, 4, 'F', 1, 1, 'business:pet:wallet:adjust:request',  '#', NOW(), NOW()),
(3225, '调账审批',     3220, 5, 'F', 1, 1, 'business:pet:wallet:adjust:approve',  '#', NOW(), NOW()),
(3226, '对账查看',     3220, 6, 'F', 1, 1, 'business:pet:wallet:read',            '#', NOW(), NOW()),
(3227, '对账触发',     3220, 7, 'F', 1, 1, 'business:pet:wallet:read',            '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管绑定新增行
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id IN (3220, 3221, 3222, 3223, 3224, 3225, 3226, 3227) AND deleted_at IS NULL;
