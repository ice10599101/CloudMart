-- PET-21：宠物权限归一化——V16 种子的 pet:* 短名与端点实际使用的 business:pet:* 全部不匹配
-- （全仓库无任何 @RequiresPermission 使用 pet:* 短名），属"已授权但永不命中"的死权限，
-- 误导运营以为授予了细粒度控制。本迁移不改历史迁移文件，按语义归一：
--
--   A. 无专属权限的菜单行（处罚/配置/作业）→ 就地改 perms 指向端点真实门禁
--      （处罚/配置 = business:pet:edit，作业只读 = business:pet:list），菜单 ID 与既有授权保留；
--   B. 与 V11 钱包权限语义重复的菜单行（钱包查询/调账申请/调账审批/冻结）→
--      角色授权迁移到 V11 行（按 perms 查找目标），死行软删除，不留两套同义名字。
--
-- 幂等：全部按 perms 条件更新/迁移，重放无额外副作用。

-- ---------------- A. 就地归一（无专属权限，端点实际门禁为宽权限） ----------------
UPDATE admin_menu SET perms = 'business:pet:edit', remark = CONCAT(IFNULL(remark, ''), '；PET-21 归一：原 pet:sanction:list 无端点使用，处罚列表门禁为 business:pet:edit')
WHERE id = 900000001 AND perms = 'pet:sanction:list' AND deleted_at IS NULL;

UPDATE admin_menu SET perms = 'business:pet:edit', remark = CONCAT(IFNULL(remark, ''), '；PET-21 归一：原 pet:sanction:revoke 无端点使用，处罚撤销门禁为 business:pet:edit')
WHERE id = 900000002 AND perms = 'pet:sanction:revoke' AND deleted_at IS NULL;

UPDATE admin_menu SET perms = 'business:pet:edit', remark = CONCAT(IFNULL(remark, ''), '；PET-21 归一：原 pet:config:publish 无端点使用，配置发布门禁为 business:pet:edit')
WHERE id = 900000006 AND perms = 'pet:config:publish' AND deleted_at IS NULL;

UPDATE admin_menu SET perms = 'business:pet:list', remark = CONCAT(IFNULL(remark, ''), '；PET-21 归一：原 pet:job:read 无端点使用，作业查询门禁为 business:pet:list')
WHERE id = 900000007 AND perms = 'pet:job:read' AND deleted_at IS NULL;

-- ---------------- B. 钱包重复权限：授权迁移到 V11 行 + 死行软删除 ----------------
-- admin_role_menu 无软删列，uk_role_menu(role_id, menu_id) 唯一键 + INSERT IGNORE 保证幂等。

-- 死行 900000003（pet:wallet:adjust:apply → V11 request）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT rm.role_id, t.id
FROM admin_role_menu rm
JOIN admin_menu d ON d.id = rm.menu_id AND d.perms = 'pet:wallet:adjust:apply' AND d.deleted_at IS NULL
JOIN admin_menu t ON t.perms = 'business:pet:wallet:adjust:request' AND t.deleted_at IS NULL;

-- 死行 900000004（pet:wallet:adjust:approve → V11 approve）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT rm.role_id, t.id
FROM admin_role_menu rm
JOIN admin_menu d ON d.id = rm.menu_id AND d.perms = 'pet:wallet:adjust:approve' AND d.deleted_at IS NULL
JOIN admin_menu t ON t.perms = 'business:pet:wallet:adjust:approve' AND t.deleted_at IS NULL;

-- 死行 900000005（pet:wallet:freeze → V11 approve：冻结端点门禁即 approve）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT rm.role_id, t.id
FROM admin_role_menu rm
JOIN admin_menu d ON d.id = rm.menu_id AND d.perms = 'pet:wallet:freeze' AND d.deleted_at IS NULL
JOIN admin_menu t ON t.perms = 'business:pet:wallet:adjust:approve' AND t.deleted_at IS NULL;

-- 死行 900000008（pet:wallet:read → V11 read）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT rm.role_id, t.id
FROM admin_role_menu rm
JOIN admin_menu d ON d.id = rm.menu_id AND d.perms = 'pet:wallet:read' AND d.deleted_at IS NULL
JOIN admin_menu t ON t.perms = 'business:pet:wallet:read' AND t.deleted_at IS NULL;

-- 迁移完成后软删除四个死行（软删与全局 @TableLogic 语义一致，可追溯）
UPDATE admin_menu SET deleted_at = NOW(),
  remark = CONCAT(IFNULL(remark, ''), '；PET-21 归一：与 business:pet:wallet:* 重复，授权已迁移后停用')
WHERE id IN (900000003, 900000004, 900000005, 900000008)
  AND perms IN ('pet:wallet:adjust:apply', 'pet:wallet:adjust:approve', 'pet:wallet:freeze', 'pet:wallet:read')
  AND deleted_at IS NULL;
