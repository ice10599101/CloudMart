-- =============================================
-- V4: 管理代理新增权限点落库（encounter 解冻 / liveWidget）
-- 背景：mall-admin 补齐 Sprint 3.3/3.4 管理代理接口，
--       边缘注解 @RequiresPermission 需要与 admin_menu.perms 对齐
-- 与 V3 的关系：V3 将 3011 perms 置 NULL（当时代理不存在、无从校验），
--   本迁移在代理落地后补回真实权限点。幂等，可重复执行。
-- =============================================

-- 直播挂件配置：C 行挂 list 权限
UPDATE admin_menu SET perms = 'business:liveWidget:list', updated_at = NOW()
WHERE id = 3011 AND deleted_at IS NULL;

-- 按钮：挂件编辑（V3 曾清理过旧的同权限行，此处用未占用的新 ID 重建）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3133, '挂件编辑', 3011, 1, 'F', 1, 1, 'business:liveWidget:edit', '#', NOW(), NOW()),
(3134, '擦肩解冻', 3012, 2, 'F', 1, 1, 'business:encounter:unfreeze', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 超管角色绑定新增行（超管本身 *:*:*，保持数据一致性）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id IN (3133, 3134) AND deleted_at IS NULL;
