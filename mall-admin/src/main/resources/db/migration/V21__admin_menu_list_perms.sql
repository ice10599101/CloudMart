-- ============================================================================
-- R31：收敛 admin_menu 空 perms 菜单的越权展示面。
--
-- 背景：GET /admin/menus/tree 对全部登录管理员下发全量菜单，前端侧边栏规则为
-- "C/M 行 perms 为空即无条件展示"（stores/adminMenu.ts toLayoutMenu）。
-- V3 时社区列表接口仅要求登录，故 4001-4009 与 2132 的 perms 置空；
-- 其后 S02（V13）等已对写操作与私聊读取加权限，但 C 行仍为空，
-- 导致没有任何社区授权的角色也能在侧边栏看到这些入口（点入后部分接口 403）。
--
-- 本次处理：
-- 1. 社区 7 个 C 菜单补 *:list 权限标识，与 V13 写操作权限点同一命名域，
--    后端对应 GET 列表接口同步加 @RequiresPermission（见 AdminCommunityController）。
-- 2. 通知发送/聊天管理 C 行与后端既有权限注解对齐
--    （business:notification:list、chat:content:read 早已在后端强制执行）。
-- 3. 通知发送补"发送"F 按钮（business:notification:send 后端早已强制，此前无授权入口）。
-- 4. 文件管理(2132) 经核实保持 NULL：页面真实存在，/file/assets 是 C 端公共资产通道
--    （登录即可用、按用户身份配额/归属授权），不挂管理员权限，见下方审计说明。
--
-- 授权影响：role_id=1 超管在 V3 §11 已全量绑定这些 C 行且 *:*:* 放行，不受影响；
-- 自定义角色沿用 V7/V13 既有约定——经菜单管理按需授予，本迁移不自动放开。
-- ============================================================================

-- 1. 社区列表 C 行补权限标识
UPDATE admin_menu SET perms = 'community:post:list'    WHERE id = 4001;
UPDATE admin_menu SET perms = 'community:review:list'  WHERE id = 4002;
UPDATE admin_menu SET perms = 'community:report:list'  WHERE id = 4003;
UPDATE admin_menu SET perms = 'community:comment:list' WHERE id = 4004;
UPDATE admin_menu SET perms = 'community:tag:list'     WHERE id = 4005;
UPDATE admin_menu SET perms = 'community:badge:list'   WHERE id = 4006;
UPDATE admin_menu SET perms = 'community:growth:list'  WHERE id = 4007;

-- 2. 通知/聊天 C 行与后端 @RequiresPermission 对齐
UPDATE admin_menu SET perms = 'business:notification:list' WHERE id = 4008;
UPDATE admin_menu SET perms = 'chat:content:read'          WHERE id = 4009;

-- 3. 通知发送按钮（后端 AdminBusinessNotificationController#send 已强制 business:notification:send）
INSERT IGNORE INTO admin_menu (id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, created_at, updated_at) VALUES
(4158, '通知发送 business:notification:send', 4008, 1, 'F', 1, 1, 'business:notification:send', '#', NOW(), NOW());

-- 4. 文件管理(2132) 经 R31 核实维持 perms = NULL（故意公开，不补"纸面权限"）：
--    前端页面存在（.umirc.ts /admin/business/file-upload → FileUpload.tsx），
--    其上传/删除走 mall-file 的 /file/assets 通道——该通道是 C 端跨租户资产通道
--    （普通用户头像等上传同样使用，按 JWT 用户身份做配额与归属授权），
--    不能挂管理员权限标识；侧边栏对登录管理员可见与后端"登录即可用"口径一致。
--    若未来需要管理端专用文件审计能力，应新增独立管理端点并配独立权限，而非给
--    C 端通道套 admin perm。

-- 超管角色（role_id=1）自动获得新增 F 权限点；自定义角色由菜单管理按需授予
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, created_at, updated_at)
SELECT 1, id, NOW(), NOW() FROM admin_menu WHERE deleted_at IS NULL AND id = 4158;
