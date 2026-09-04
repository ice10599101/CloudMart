-- =============================================
-- V6: 心愿虚拟资产 删除权限点落库
-- 背景：虚拟资产新增删除功能（仅允许删除无用户持有的配置行）
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE
-- =============================================

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3137, '资产删除', 3018, 3, 'F', 1, 1, 'business:asset:remove', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管绑定新增行
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id = 3137 AND deleted_at IS NULL;
