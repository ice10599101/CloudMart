-- T11：对账执行/差异处置按钮权限——与 sql/business-payment-reconcile-perms.sql 种子同源，
-- 改走 Flyway 版本化（远程环境该种子脚本从未执行，business:payment:reconcile 一直缺
-- 菜单行，自定义角色无从授权；超管 *:*:* 通配不受影响）。
-- 父菜单动态解析为 perms='business:payment:list' 的 C 行（支付管理 2070）；查不到则
-- 零行插入安全空转。id 沿用种子脚本固定值 960019001/960019002，两处幂等互斥。

-- 1) 执行对账按钮
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, component, query, route_name,
                        is_frame, is_cache, menu_type, visible, status, perms, icon, remark,
                        created_at, updated_at)
SELECT 960019001, '执行对账', parent.id, 10, '', '', '', '',
       1, 0, 'F', 0, 0, 'business:payment:reconcile', '', 'OPS-01 支付对账执行（T11 三层 scope）',
       NOW(), NOW()
FROM (
    SELECT id FROM admin_menu
    WHERE perms = 'business:payment:list' AND menu_type = 'C' AND deleted_at IS NULL
    LIMIT 1
) AS parent
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE id = 960019001);

-- 2) 处置差异按钮
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, component, query, route_name,
                        is_frame, is_cache, menu_type, visible, status, perms, icon, remark,
                        created_at, updated_at)
SELECT 960019002, '处置差异', parent.id, 11, '', '', '', '',
       1, 0, 'F', 0, 0, 'business:payment:reconcile', '', 'OPS-01 对账差异人工处置（不直接改资金）',
       NOW(), NOW()
FROM (
    SELECT id FROM admin_menu
    WHERE perms = 'business:payment:list' AND menu_type = 'C' AND deleted_at IS NULL
    LIMIT 1
) AS parent
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE id = 960019002);

-- 3) 超管角色绑定（与 V14 同模式；超管权限通配本即可用，绑定保持菜单树一致性）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu
WHERE id IN (960019001, 960019002) AND deleted_at IS NULL;
