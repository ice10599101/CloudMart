-- =============================================
-- V10: 心愿治理工作台（N01）菜单与权限点落库
-- 对齐 §8.1：business:wishModeration:* / business:wishAppeal:review
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE
-- =============================================

-- 治理工作台（C 行，path 对齐 .umirc /admin/business/wish-moderation）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3200, '心愿治理工作台', 2000, 50, '/admin/business/wish-moderation', 'C', 1, 1, 'business:wishModeration:list', 'audit', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3201, '治理队列',   3200, 1, 'F', 1, 1, 'business:wishModeration:list',  '#', NOW(), NOW()),
(3202, '工单详情',   3200, 2, 'F', 1, 1, 'business:wishModeration:query', '#', NOW(), NOW()),
(3203, '治理决定',   3200, 3, 'F', 1, 1, 'business:wishModeration:audit', '#', NOW(), NOW()),
(3204, '申诉复核',   3200, 4, 'F', 1, 1, 'business:wishAppeal:review',    '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管绑定新增行
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id IN (3200, 3201, 3202, 3203, 3204) AND deleted_at IS NULL;
