-- =============================================================================
-- business:payment:reconcile 权限码种子（幂等，可重复执行）
--
-- 背景：支付对账「执行/处置差异」端点使用细粒度权限码 business:payment:reconcile，
-- 不再复用 business:payment:refund。admin_menu 为运行时维护表（无迁移框架），
-- 本脚本补齐两个按钮级权限记录；自定义角色请再经「系统管理→角色管理」勾选授予。
-- 超级管理员（role key = admin）权限集为 *:*:* 通配，不受本脚本影响。
--
-- 父菜单解析：优先挂在 perms='business:payment:list' 且 menu_type='C' 的支付管理
-- 菜单下；查不到 C 行时回退挂到同 perms 的按钮行下。两处都查不到（该环境从未
-- 配置支付管理菜单）时 INSERT..SELECT 零行插入，脚本安全空转——此时请在
-- 「系统管理→菜单管理」手工创建按钮并填写权限标识 business:payment:reconcile。
--
-- 兼容性：id 取低位段固定值（960019001/960019002），远小于 MyBatis-Plus ASSIGN_ID
-- 雪花 ID（约 10^18 量级），不会冲突；deleted_at 逻辑删除行不参与查重与父级解析；
-- 查重按主键 id 逐行幂等（两行共用同一权限码）。
-- =============================================================================

-- 1) 执行对账按钮
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, component, query, route_name,
                        is_frame, is_cache, menu_type, visible, status, perms, icon, remark,
                        created_at, updated_at)
SELECT 960019001, '执行对账', parent.id, 10, '', '', '', '',
       1, 0, 'F', 0, 0, 'business:payment:reconcile', '', 'OPS-01 支付对账执行/差异处置',
       NOW(), NOW()
FROM (
    (SELECT id FROM admin_menu
     WHERE perms = 'business:payment:list' AND menu_type = 'C' AND deleted_at IS NULL
     LIMIT 1)
    UNION ALL
    (SELECT id FROM admin_menu
     WHERE perms = 'business:payment:list' AND menu_type <> 'C' AND deleted_at IS NULL
       AND NOT EXISTS (SELECT 1 FROM admin_menu
                       WHERE perms = 'business:payment:list' AND menu_type = 'C' AND deleted_at IS NULL)
     LIMIT 1)
) AS parent
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE id = 960019001)
  AND EXISTS (SELECT 1 FROM admin_menu WHERE perms = 'business:payment:list' AND deleted_at IS NULL);

-- 2) 处置差异按钮（与执行对账共用同一权限码，分两行便于菜单树独立命名/授权展示）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, component, query, route_name,
                        is_frame, is_cache, menu_type, visible, status, perms, icon, remark,
                        created_at, updated_at)
SELECT 960019002, '对账差异处置', parent.id, 11, '', '', '', '',
       1, 0, 'F', 0, 0, 'business:payment:reconcile', '', 'OPS-01 对账差异人工处置（登记证据/说明）',
       NOW(), NOW()
FROM (
    (SELECT id FROM admin_menu
     WHERE perms = 'business:payment:list' AND menu_type = 'C' AND deleted_at IS NULL
     LIMIT 1)
    UNION ALL
    (SELECT id FROM admin_menu
     WHERE perms = 'business:payment:list' AND menu_type <> 'C' AND deleted_at IS NULL
       AND NOT EXISTS (SELECT 1 FROM admin_menu
                       WHERE perms = 'business:payment:list' AND menu_type = 'C' AND deleted_at IS NULL)
     LIMIT 1)
) AS parent
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE id = 960019002)
  AND EXISTS (SELECT 1 FROM admin_menu WHERE perms = 'business:payment:list' AND deleted_at IS NULL);

-- 校验：应返回 2 行（或此前已插入时的既有行数）
SELECT id, menu_name, parent_id, perms FROM admin_menu
WHERE perms = 'business:payment:reconcile' AND deleted_at IS NULL;
