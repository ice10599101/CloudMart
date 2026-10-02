-- T11：售后案件处置页菜单——挂在业务管理（2000）下，与订单管理（2020）并列。
-- 页面路由 /admin/business/after-sale-cases（CloudMart-ui 文件路由自动注册），
-- 权限沿用 business:order:refund（与退款审批一致，售后受理即退款动作）。

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(2021, '售后案件', 2000, 2, '/admin/business/after-sale-cases', 'C', 1, 1, 'business:order:refund', 'form', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管角色（role_id=1）自动可见；自定义角色由菜单管理按需授予
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu WHERE deleted_at IS NULL AND id = 2021;
