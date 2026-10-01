-- S02：后台接口权限全覆盖——社区细粒度权限点与敏感聊天访问权限。
-- 审计锚点：AdminCommunityController 帖子状态修改/标签/举报/徽章/成长配置/审核动作；
-- AdminChatController 私聊正文读取独立权限（chat:content:read）。
-- 种子沿用 V7 模式：INSERT IGNORE + 超管角色（role_id=1）自动授权；
-- 自定义角色经菜单管理按需授予（不自动授予全部旧角色）。
INSERT IGNORE INTO admin_menu (id,menu_name,parent_id,order_num,menu_type,visible,status,perms,icon,created_at,updated_at) VALUES
(4150,'帖子状态管理 community:post:edit',4001,3,'F',1,1,'community:post:edit','#',NOW(),NOW()),
(4151,'帖子审核 community:post:moderate',4002,2,'F',1,1,'community:post:moderate','#',NOW(),NOW()),
(4152,'标签新增 community:tag:write',4005,2,'F',1,1,'community:tag:write','#',NOW(),NOW()),
(4153,'举报处理 community:report:handle',4003,1,'F',1,1,'community:report:handle','#',NOW(),NOW()),
(4154,'徽章新增 community:badge:write',4006,2,'F',1,1,'community:badge:write','#',NOW(),NOW()),
(4155,'徽章授予 community:badge:grant',4006,3,'F',1,1,'community:badge:grant','#',NOW(),NOW()),
(4156,'成长配置写入 community:growth:write',4007,2,'F',1,1,'community:growth:write','#',NOW(),NOW()),
(4157,'私聊正文读取 chat:content:read',4009,1,'F',1,1,'chat:content:read','#',NOW(),NOW());

-- 超管角色（role_id=1）自动获得新权限点；自定义角色由菜单管理按需授予
INSERT IGNORE INTO admin_role_menu (role_id,menu_id,created_at,updated_at)
SELECT 1,id,NOW(),NOW() FROM admin_menu WHERE deleted_at IS NULL AND id>=4150;
