-- T09 代码生成器退役（数据层）：
-- mall-gen 无业务源码、不在 reactor、无网关路由（/api/gen/** 探针 404），
-- V10 已删除工具目录与“代码生成”菜单（6000/6001），但遗留 3 个 F 型按钮权限
-- （tool:gen:code/preview/query，父级 1000），会导致角色仍持有生成器权限。
-- 本迁移清理按钮权限及其角色授权；不新增任何替代能力。

DELETE FROM `admin_role_menu`
WHERE `menu_id` IN (SELECT `id` FROM `admin_menu` WHERE `perms` LIKE 'tool:gen:%');

DELETE FROM `admin_menu` WHERE `perms` LIKE 'tool:gen:%';
