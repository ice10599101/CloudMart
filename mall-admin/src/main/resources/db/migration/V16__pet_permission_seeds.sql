-- V16 (§8.1): 宠物域细分权限种子——按方案 §8.1 权限清单登记
-- 挂到既有"宠物管理"菜单（business:pet:edit 所在菜单）之下，授予超级管理员角色（role_id=1）。
-- 幂等：固定 ID + INSERT IGNORE。

-- 1) 菜单/权限项（挂在宠物管理父菜单下）
INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at)
SELECT 900000001, '宠物处罚管理', m.parent_id, 20, '#', 'C', '1', '0', 'pet:sanction:list', '🔒', NOW(), NOW()
FROM admin_menu m WHERE m.perms = 'business:pet:edit' LIMIT 1;

INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at)
SELECT 900000002, '宠物处罚撤销', m.parent_id, 21, '#', 'F', '1', '0', 'pet:sanction:revoke', '🔓', NOW(), NOW()
FROM admin_menu m WHERE m.perms = 'business:pet:edit' LIMIT 1;

INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at)
SELECT 900000003, '宠物钱包调账申请', m.parent_id, 22, '#', 'F', '1', '0', 'pet:wallet:adjust:apply', '💰', NOW(), NOW()
FROM admin_menu m WHERE m.perms = 'business:pet:edit' LIMIT 1;

INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at)
SELECT 900000004, '宠物钱包调账审批', m.parent_id, 23, '#', 'F', '1', '0', 'pet:wallet:adjust:approve', '✅', NOW(), NOW()
FROM admin_menu m WHERE m.perms = 'business:pet:edit' LIMIT 1;

INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at)
SELECT 900000005, '宠物钱包冻结', m.parent_id, 24, '#', 'F', '1', '0', 'pet:wallet:freeze', '❄️', NOW(), NOW()
FROM admin_menu m WHERE m.perms = 'business:pet:edit' LIMIT 1;

INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at)
SELECT 900000006, '宠物配置发布', m.parent_id, 25, '#', 'F', '1', '0', 'pet:config:publish', '📦', NOW(), NOW()
FROM admin_menu m WHERE m.perms = 'business:pet:edit' LIMIT 1;

INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at)
SELECT 900000007, '宠物作业中心', m.parent_id, 26, '#', 'C', '1', '0', 'pet:job:read', '🗂️', NOW(), NOW()
FROM admin_menu m WHERE m.perms = 'business:pet:edit' LIMIT 1;

INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at)
SELECT 900000008, '宠物对账查询', m.parent_id, 27, '#', 'C', '1', '0', 'pet:wallet:read', '📊', NOW(), NOW()
FROM admin_menu m WHERE m.perms = 'business:pet:edit' LIMIT 1;

-- 2) 授予超级管理员角色（role_id=1，与 V15 语义一致）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id IN (900000001, 900000002, 900000003, 900000004, 900000005, 900000006, 900000007, 900000008)
  AND deleted_at IS NULL;
