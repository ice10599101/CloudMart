-- =============================================
-- V20: 公共胶囊墙审核权限点（R17）
-- 背景：mall-admin 新增 /wish/capsules/wall/** 代理（待审核/已上墙/裁决/下架），
--       3015「时间胶囊」菜单下补 F 行权限 business:capsule:audit。
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE（对齐 V8/V19 模式）
-- =============================================

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3145, '胶囊墙审核', 3015, 1, 'F', 1, 1, 'business:capsule:audit', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管角色（role_id=1）自动可见；自定义角色由菜单管理按需授予
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id = 3145 AND deleted_at IS NULL;
