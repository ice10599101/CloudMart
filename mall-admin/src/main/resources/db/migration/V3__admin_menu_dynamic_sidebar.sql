-- =============================================
-- V3: 管理端侧边栏菜单动态化数据收敛
-- 目标：admin_menu 成为侧边栏唯一数据源，前端 AdminLayout 不再硬编码 MENU_CONFIG
-- 约定（与 admin_menu 表注释一致）：
--   menu_type: M-目录, C-菜单, F-按钮
--   visible:   0-隐藏, 1-显示
--   status:    0-停用, 1-正常
--   path:      统一存前端完整路由（带前导 /），与 .umirc.ts 路由一一对应
--   perms:     与后端 @RequiresPermission 注解完全一致；无后端校验的页面为 NULL
-- 幂等：固定 ID + ON DUPLICATE KEY UPDATE / DELETE，可重复执行
-- =============================================

-- ========== 1. 清理脏数据 ==========

-- 实验残留：挂在「管理员管理(1001)」下的背景音乐及其按钮（与 3006 重复）
DELETE FROM admin_role_menu WHERE menu_id IN (2093353998403764226, 2093355392376504322);
DELETE FROM admin_menu WHERE id IN (2093353998403764226, 2093355392376504322);

-- 重复/已软删的历史行（含两条背景音乐重复行）
DELETE FROM admin_role_menu
 WHERE menu_id IN (SELECT id FROM (SELECT id FROM admin_menu WHERE deleted_at IS NOT NULL) t);
DELETE FROM admin_menu WHERE deleted_at IS NOT NULL;

-- 分类管理按钮的 perms（business:category:*）后端不存在，分类接口实际校验 business:product:*
DELETE FROM admin_role_menu WHERE menu_id IN (2011, 2012, 2013, 2014);
DELETE FROM admin_menu WHERE id IN (2011, 2012, 2013, 2014);

-- 擦肩而过风控/温暖事件审核：path 为空且无前端页面，
-- 功能已由 3012「LBS 隐私审计」(MapAdmin) 整合（围栏/温暖事件/LBS 可疑点/冻结）
DELETE FROM admin_role_menu WHERE menu_id IN (3016, 3017, 3118, 3125, 3126, 3127);
DELETE FROM admin_menu WHERE id IN (3016, 3017, 3118, 3125, 3126, 3127);

-- 心愿编辑/删除按钮：后端心愿写接口仅校验 business:wish:audit 等既有权限，
-- business:wish:edit/remove 权限标识不存在（挂件编辑 business:liveWidget:edit 同理）
DELETE FROM admin_role_menu WHERE menu_id IN (3101, 3103, 3117);
DELETE FROM admin_menu WHERE id IN (3101, 3103, 3117);

-- ========== 2. 根级菜单 ==========

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(100, '工作台', 0, 0, '/admin/dashboard', 'C', 1, 1, NULL, 'dashboard', NOW(), NOW()),
(1000, '系统管理', 0, 1, '/admin/system', 'M', 1, 1, NULL, 'setting', NOW(), NOW()),
(2000, '业务管理', 0, 2, '/admin/business', 'M', 1, 1, NULL, 'shopping', NOW(), NOW()),
(4000, '社区管理', 0, 3, '/admin/community', 'M', 1, 1, NULL, 'flag', NOW(), NOW()),
(5000, '监控管理', 0, 4, '/admin/monitor', 'M', 1, 1, NULL, 'monitor', NOW(), NOW()),
(6000, '工具', 0, 5, '/admin/tool', 'M', 1, 1, NULL, 'tool', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- ========== 3. 系统管理：路径/名称修正 ==========

UPDATE admin_menu SET menu_name = '用户管理', path = '/admin/system/users',     icon = 'user',      updated_at = NOW() WHERE id = 1001;
UPDATE admin_menu SET menu_name = '角色管理', path = '/admin/system/roles',    icon = 'peoples',   updated_at = NOW() WHERE id = 1010;
UPDATE admin_menu SET menu_name = '菜单管理', path = '/admin/system/menus',    icon = 'tree-table', updated_at = NOW() WHERE id = 1020;
UPDATE admin_menu SET menu_name = '字典管理', path = '/admin/system/dict',     icon = 'dict',      updated_at = NOW() WHERE id = 1030;
UPDATE admin_menu SET menu_name = '参数设置', path = '/admin/system/config',   icon = 'edit',      updated_at = NOW() WHERE id = 1040;
UPDATE admin_menu SET                          path = '/admin/system/log',     icon = 'log',       updated_at = NOW() WHERE id = 1050;
UPDATE admin_menu SET                          path = '/admin/system/oper-log',  icon = 'form',    updated_at = NOW() WHERE id = 1051;
UPDATE admin_menu SET                          path = '/admin/system/login-log', icon = 'logininfor', updated_at = NOW() WHERE id = 1054;

-- ========== 4. 系统管理：缺失的部门/岗位/通知公告 ==========

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(1006, '部门管理', 1000, 4, '/admin/system/depts',   'C', 1, 1, 'admin:dept:list',   'tree', NOW(), NOW()),
(1025, '岗位管理', 1000, 5, '/admin/system/posts',   'C', 1, 1, 'admin:post:list',   'user', NOW(), NOW()),
(1045, '通知公告', 1000, 8, '/admin/system/notices', 'C', 1, 1, 'admin:notice:list', 'message', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(1007, '部门查询', 1006, 1, 'F', 1, 1, 'admin:dept:query',  '#', NOW(), NOW()),
(1008, '部门新增', 1006, 2, 'F', 1, 1, 'admin:dept:add',    '#', NOW(), NOW()),
(1009, '部门修改', 1006, 3, 'F', 1, 1, 'admin:dept:edit',   '#', NOW(), NOW()),
(1019, '部门删除', 1006, 4, 'F', 1, 1, 'admin:dept:remove', '#', NOW(), NOW()),
(1026, '岗位查询', 1025, 1, 'F', 1, 1, 'admin:post:query',  '#', NOW(), NOW()),
(1027, '岗位新增', 1025, 2, 'F', 1, 1, 'admin:post:add',    '#', NOW(), NOW()),
(1028, '岗位修改', 1025, 3, 'F', 1, 1, 'admin:post:edit',   '#', NOW(), NOW()),
(1029, '岗位删除', 1025, 4, 'F', 1, 1, 'admin:post:remove', '#', NOW(), NOW()),
(1046, '公告查询', 1045, 1, 'F', 1, 1, 'admin:notice:query',  '#', NOW(), NOW()),
(1047, '公告新增', 1045, 2, 'F', 1, 1, 'admin:notice:add',    '#', NOW(), NOW()),
(1048, '公告修改', 1045, 3, 'F', 1, 1, 'admin:notice:edit',   '#', NOW(), NOW()),
(1049, '公告删除', 1045, 4, 'F', 1, 1, 'admin:notice:remove', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- ========== 5. 业务管理：既有行路径修正（短路径 → 前端完整路由） ==========

UPDATE admin_menu SET menu_name = '商品管理',   path = '/admin/business/products',         icon = 'goods',        updated_at = NOW() WHERE id = 2001;
UPDATE admin_menu SET menu_name = '分类管理',   path = '/admin/business/categories',       icon = 'tree-table',   updated_at = NOW() WHERE id = 2010;
UPDATE admin_menu SET menu_name = '订单管理',   path = '/admin/business/orders',           icon = 'list',         updated_at = NOW() WHERE id = 2020;
UPDATE admin_menu SET menu_name = '优惠券管理', path = '/admin/business/coupons',          icon = 'money',        updated_at = NOW() WHERE id = 2030;
UPDATE admin_menu SET menu_name = '会员管理',   path = '/admin/business/members',          icon = 'peoples',      updated_at = NOW() WHERE id = 2040;
UPDATE admin_menu SET menu_name = '秒杀管理',   path = '/admin/business/seckill',          icon = 'time',         updated_at = NOW() WHERE id = 2050;
UPDATE admin_menu SET menu_name = '库存管理',   path = '/admin/business/inventory',        icon = 'box',          updated_at = NOW() WHERE id = 2060;
UPDATE admin_menu SET menu_name = '支付管理',   path = '/admin/business/payments',         icon = 'money',        updated_at = NOW() WHERE id = 2070;
UPDATE admin_menu SET menu_name = '通知管理',   path = '/admin/business/notifications',    icon = 'message',      updated_at = NOW() WHERE id = 2080;
UPDATE admin_menu SET menu_name = '购物车管理', path = '/admin/business/cart',             icon = 'shopping-cart', updated_at = NOW() WHERE id = 2090;

-- 心愿相关既有行：补前导斜杠 + 重排 order_num（3001-3015 区间 24-38）
UPDATE admin_menu SET path = '/admin/business/wishes',           order_num = 24, updated_at = NOW() WHERE id = 3001;
UPDATE admin_menu SET path = '/admin/business/wish-categories',  order_num = 25, updated_at = NOW() WHERE id = 3002;
UPDATE admin_menu SET path = '/admin/business/wish-interactions', order_num = 26, updated_at = NOW() WHERE id = 3003;
UPDATE admin_menu SET path = '/admin/business/wish-comments',    order_num = 27, updated_at = NOW() WHERE id = 3004;
UPDATE admin_menu SET path = '/admin/business/wish-badges',      order_num = 28, updated_at = NOW() WHERE id = 3005;
UPDATE admin_menu SET path = '/admin/business/wish-bgm',         order_num = 29, updated_at = NOW() WHERE id = 3006;
UPDATE admin_menu SET path = '/admin/business/activity',         order_num = 30, updated_at = NOW() WHERE id = 3007;
UPDATE admin_menu SET path = '/admin/business/tree-env',         order_num = 31, updated_at = NOW() WHERE id = 3014;
UPDATE admin_menu SET path = '/admin/business/capsules',         order_num = 32, updated_at = NOW() WHERE id = 3015;
UPDATE admin_menu SET path = '/admin/business/wish-ai',          order_num = 33, updated_at = NOW() WHERE id = 3013;
UPDATE admin_menu SET path = '/admin/business/match',            order_num = 34, updated_at = NOW() WHERE id = 3008;
UPDATE admin_menu SET path = '/admin/business/legacy',           order_num = 35, updated_at = NOW() WHERE id = 3009;
UPDATE admin_menu SET path = '/admin/business/grayscale',        order_num = 36, updated_at = NOW() WHERE id = 3010;
UPDATE admin_menu SET path = '/admin/business/map-audit',        order_num = 37, updated_at = NOW() WHERE id = 3012;
UPDATE admin_menu SET path = '/admin/business/live-widget',      order_num = 38, updated_at = NOW() WHERE id = 3011;

-- ========== 6. 业务管理：缺失页面补齐 ==========

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(2035, '评价管理',     2000, 9,  '/admin/business/reviews',           'C', 1, 1, 'business:review:list',   'comment', NOW(), NOW()),
(2045, '拼团活动',     2000, 12, '/admin/business/group-activity',    'C', 1, 1, 'business:marketing:list', 'team',   NOW(), NOW()),
(2046, '阶梯促销',     2000, 13, '/admin/business/tiered-promotion',  'C', 1, 1, 'business:marketing:list', 'rise',   NOW(), NOW()),
(2093, '直播管理',     2000, 14, '/admin/business/live',              'C', 1, 1, 'business:live:list',     'video',   NOW(), NOW()),
(2094, '仓储管理',     2000, 15, '/admin/business/wms',               'C', 1, 1, 'business:wms:list',      'database', NOW(), NOW()),
(2095, '黑名单管理',   2000, 16, '/admin/business/blacklist',         'C', 1, 1, 'business:risk:list',     'stop',    NOW(), NOW()),
(2096, 'AI 管理',      2000, 17, '/admin/business/ai',                'C', 1, 1, 'business:aiReview:list', 'robot',   NOW(), NOW()),
(2097, '品牌管理',     2000, 18, '/admin/business/brands',            'C', 1, 1, 'business:brand:list',    'crown',   NOW(), NOW()),
(2098, '风控记录',     2000, 19, '/admin/business/risk-records',      'C', 1, 1, 'business:risk:list',     'safety',  NOW(), NOW()),
(2099, '风控规则',     2000, 20, '/admin/business/risk-rules',        'C', 1, 1, 'business:risk:list',     'safety',  NOW(), NOW()),
(2130, '物流管理',     2000, 21, '/admin/business/shipping',          'C', 1, 1, 'business:shipping:list', 'car',     NOW(), NOW()),
(2131, '仓库管理',     2000, 22, '/admin/business/warehouses',        'C', 1, 1, 'business:warehouse:list', 'database', NOW(), NOW()),
(2132, '文件管理',     2000, 23, '/admin/business/file-upload',       'C', 1, 1, 'business:file:list',     'upload',  NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 购物车编辑按钮（后端存在 business:cart:edit 校验）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(2092, '购物车编辑', 2090, 2, 'F', 1, 1, 'business:cart:edit', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- ========== 7. 社区管理 ==========
-- 列表接口仅需登录（后端无 list 权限注解），C 行 perms 为 NULL；
-- 写操作按后端注解挂 F 按钮

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(4001, '帖子管理', 4000, 1, '/admin/community/posts',         'C', 1, 1, NULL, 'unordered-list', NOW(), NOW()),
(4002, '内容审核', 4000, 2, '/admin/community/review',        'C', 1, 1, NULL, 'safety',         NOW(), NOW()),
(4003, '举报管理', 4000, 3, '/admin/community/reports',       'C', 1, 1, NULL, 'alert',          NOW(), NOW()),
(4004, '评论管理', 4000, 4, '/admin/community/comments',      'C', 1, 1, NULL, 'comment',        NOW(), NOW()),
(4005, '标签管理', 4000, 5, '/admin/community/tags',          'C', 1, 1, NULL, 'tag',            NOW(), NOW()),
(4006, '徽章管理', 4000, 6, '/admin/community/badges',        'C', 1, 1, NULL, 'trophy',         NOW(), NOW()),
(4007, '成长配置', 4000, 7, '/admin/community/growth',        'C', 1, 1, NULL, 'rise',           NOW(), NOW()),
(4008, '通知发送', 4000, 8, '/admin/community/notifications', 'C', 1, 1, NULL, 'notification',   NOW(), NOW()),
(4009, '聊天管理', 4000, 9, '/admin/community/chat',          'C', 1, 1, NULL, 'message',        NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(4011, '帖子编辑', 4001, 1, 'F', 1, 1, 'community:post:edit',    '#', NOW(), NOW()),
(4012, '帖子删除', 4001, 2, 'F', 1, 1, 'community:post:remove',  '#', NOW(), NOW()),
(4013, '内容审核', 4002, 1, 'F', 1, 1, 'community:review:edit',  '#', NOW(), NOW()),
(4014, '评论编辑', 4004, 1, 'F', 1, 1, 'community:comment:edit', '#', NOW(), NOW()),
(4015, '评论删除', 4004, 2, 'F', 1, 1, 'community:comment:remove', '#', NOW(), NOW()),
(4016, '标签编辑', 4005, 1, 'F', 1, 1, 'community:tag:edit',     '#', NOW(), NOW()),
(4017, '徽章编辑', 4006, 1, 'F', 1, 1, 'community:badge:edit',   '#', NOW(), NOW()),
(4018, '成长编辑', 4007, 1, 'F', 1, 1, 'community:growth:edit',  '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- ========== 8. 监控管理 + 工具 ==========

INSERT INTO admin_menu (id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(5001, '定时任务', 5000, 1, '/admin/monitor/job',    'C', 1, 1, 'monitor:job:list',   'schedule', NOW(), NOW()),
(5002, '服务监控', 5000, 2, '/admin/monitor/server', 'C', 1, 1, 'monitor:server:list', 'monitor',  NOW(), NOW()),
(5003, '缓存监控', 5000, 3, '/admin/monitor/cache',  'C', 1, 1, 'monitor:cache:list', 'database', NOW(), NOW()),
(5004, '在线用户', 5000, 4, '/admin/monitor/online', 'C', 1, 1, 'admin:online:list',  'user',     NOW(), NOW()),
(6001, '代码生成', 6000, 1, '/admin/tool/gen',       'C', 1, 1, 'tool:gen:list',      'code',     NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  path = VALUES(path), menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- ========== 9. 心愿按钮 perms 对齐后端注解 ==========

-- 3105/3106 是徽章按钮，DB 里错挂了 BGM 权限标识
UPDATE admin_menu SET perms = 'business:wishBadge:add'  WHERE id = 3105;
UPDATE admin_menu SET perms = 'business:wishBadge:edit' WHERE id = 3106;

-- 背景音乐按钮（后端校验 business:wishBgm:add/edit/delete）
INSERT INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(3130, '背景音乐新增', 3006, 1, 'F', 1, 1, 'business:wishBgm:add',    '#', NOW(), NOW()),
(3131, '背景音乐编辑', 3006, 2, 'F', 1, 1, 'business:wishBgm:edit',   '#', NOW(), NOW()),
(3132, '背景音乐删除', 3006, 3, 'F', 1, 1, 'business:wishBgm:delete', '#', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num),
  menu_type = VALUES(menu_type), visible = VALUES(visible), status = VALUES(status),
  perms = VALUES(perms), icon = VALUES(icon), updated_at = NOW();

-- 直播挂件配置/文件管理：代理接口仅要求登录、无权限注解，perms 置空与后端一致
UPDATE admin_menu SET perms = NULL WHERE id IN (3011, 2132);

-- ========== 10. 全量收敛可见/状态为默认显示 ==========

UPDATE admin_menu SET visible = 1, status = 1 WHERE deleted_at IS NULL;

-- ========== 11. 超级管理员角色绑定全量菜单（超级管理员本身 *:*:*，此处保持数据一致性） ==========

INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu WHERE deleted_at IS NULL;
