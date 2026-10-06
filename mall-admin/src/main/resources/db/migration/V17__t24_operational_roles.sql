-- V17 (T24)：多运营最小角色集 + 管理员账号管理菜单。
-- 方案 T24：多运营阶段启用现有角色/权限基础并补管理员账号/角色页面；
-- 最小角色为客服、内容运营、仓储、财务、系统管理，权限以接口动作拆分
-- （运营登录后经 角色管理 页面按需勾选菜单/权限点，本迁移只建骨架不绑菜单——
--  权限分配走 UI 可审计可回查，避免 seed 硬编码 36+ 菜单映射）。

-- 五个最小角色（role_key 唯一；data_scope: 1=全部数据）
INSERT IGNORE INTO admin_role (id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, remark, created_at, updated_at)
SELECT * FROM (SELECT
    91000001 AS id,
    '客服运营' AS role_name, 'customer_service' AS role_key, 10 AS role_sort,
    1 AS data_scope, 1 AS menu_check_strictly, 1 AS dept_check_strictly, 1 AS status,
    'T24 最小角色：工单/会话/通知/会员查询等客服动作' AS remark, NOW() AS created_at, NOW() AS updated_at
) AS t
WHERE NOT EXISTS (SELECT 1 FROM admin_role WHERE role_key = 'customer_service');

INSERT IGNORE INTO admin_role (id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, remark, created_at, updated_at)
SELECT * FROM (SELECT
    91000002 AS id,
    '内容运营' AS role_name, 'content_ops' AS role_key, 11 AS role_sort,
    1 AS data_scope, 1 AS menu_check_strictly, 1 AS dept_check_strictly, 1 AS status,
    'T24 最小角色：心愿/社区内容审核、勋章/活动运营' AS remark, NOW() AS created_at, NOW() AS updated_at
) AS t
WHERE NOT EXISTS (SELECT 1 FROM admin_role WHERE role_key = 'content_ops');

INSERT IGNORE INTO admin_role (id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, remark, created_at, updated_at)
SELECT * FROM (SELECT
    91000003 AS id,
    '仓储运营' AS role_name, 'warehouse_ops' AS role_key, 12 AS role_sort,
    1 AS data_scope, 1 AS menu_check_strictly, 1 AS dept_check_strictly, 1 AS status,
    'T24 最小角色：发货/拣选/入库/物流跟踪' AS remark, NOW() AS created_at, NOW() AS updated_at
) AS t
WHERE NOT EXISTS (SELECT 1 FROM admin_role WHERE role_key = 'warehouse_ops');

INSERT IGNORE INTO admin_role (id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, remark, created_at, updated_at)
SELECT * FROM (SELECT
    91000004 AS id,
    '财务运营' AS role_name, 'finance_ops' AS role_key, 13 AS role_sort,
    1 AS data_scope, 1 AS menu_check_strictly, 1 AS dept_check_strictly, 1 AS status,
    'T24 最小角色：支付对账/退款人工处置/券批次预算' AS remark, NOW() AS created_at, NOW() AS updated_at
) AS t
WHERE NOT EXISTS (SELECT 1 FROM admin_role WHERE role_key = 'finance_ops');

INSERT IGNORE INTO admin_role (id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, remark, created_at, updated_at)
SELECT * FROM (SELECT
    91000005 AS id,
    '系统管理员' AS role_name, 'system_admin' AS role_key, 14 AS role_sort,
    1 AS data_scope, 1 AS menu_check_strictly, 1 AS dept_check_strictly, 1 AS status,
    'T24 最小角色：账号/角色/菜单/参数配置（不含超级管理员通配）' AS remark, NOW() AS created_at, NOW() AS updated_at
) AS t
WHERE NOT EXISTS (SELECT 1 FROM admin_role WHERE role_key = 'system_admin');

-- 管理员账号管理菜单（挂在"系统管理 1000"下；1002 空闲）
-- 注意与"用户管理 1001（C 端会员）"区分：本页管理后台运营账号
INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
  (1002, '账号管理', 1000, 2, '/admin/system/admin-users', 'C', 1, 1, 'admin:user:list', 'peoples', NOW(), NOW());

-- 账号管理下的动作按钮权限（后端 @RequiresPermission 对齐）
INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
  (100201, '账号查询', 1002, 1, 'F', 1, 1, 'admin:user:query',  '#', NOW(), NOW()),
  (100202, '账号新增', 1002, 2, 'F', 1, 1, 'admin:user:add',    '#', NOW(), NOW()),
  (100203, '账号修改', 1002, 3, 'F', 1, 1, 'admin:user:edit',   '#', NOW(), NOW()),
  (100204, '账号删除', 1002, 4, 'F', 1, 1, 'admin:user:remove', '#', NOW(), NOW()),
  (100205, '重置密码', 1002, 5, 'F', 1, 1, 'admin:user:resetPwd', '#', NOW(), NOW());

-- 角色管理动作按钮权限（菜单 1010 早已存在，仅补 F 级按钮）
INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
  (101001, '角色查询', 1010, 1, 'F', 1, 1, 'admin:role:query',  '#', NOW(), NOW()),
  (101002, '角色新增', 1010, 2, 'F', 1, 1, 'admin:role:add',    '#', NOW(), NOW()),
  (101003, '角色修改', 1010, 3, 'F', 1, 1, 'admin:role:edit',   '#', NOW(), NOW()),
  (101004, '角色删除', 1010, 4, 'F', 1, 1, 'admin:role:remove', '#', NOW(), NOW());
