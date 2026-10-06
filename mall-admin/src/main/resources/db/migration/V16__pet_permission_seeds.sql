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

-- 2) 授予超级管理员角色（role_id=1，与 V15 语义一致）。
-- PET-24：admin_role_menu.id 为雪花主键无默认值——原写法缺 id 被 INSERT IGNORE 静默吞行
-- （第一行 id=0，其余 PK 冲突跳过），授权从未生效；改为显式 id 逐行插入。
INSERT IGNORE INTO admin_role_menu (id, role_id, menu_id, created_at, updated_at) VALUES
  (920000001, 1, 900000001, NOW(), NOW()),
  (920000002, 1, 900000002, NOW(), NOW()),
  (920000003, 1, 900000003, NOW(), NOW()),
  (920000004, 1, 900000004, NOW(), NOW()),
  (920000005, 1, 900000005, NOW(), NOW()),
  (920000006, 1, 900000006, NOW(), NOW()),
  (920000007, 1, 900000007, NOW(), NOW()),
  (920000008, 1, 900000008, NOW(), NOW());
