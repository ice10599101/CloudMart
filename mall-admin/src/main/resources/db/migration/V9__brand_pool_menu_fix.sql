-- WISH-03：品牌与许愿池菜单挂到"心愿管理"目录（V8 误挂 1030 字典管理——数据修正）；
-- 组件复用心愿资产管理页（品牌审批 tab 已在其内）。
UPDATE `admin_menu` SET `parent_id` = 2000, `order_num` = 8, `path` = 'wish-assets'
WHERE `id` = 2065 AND `parent_id` <> 2000;

-- 超管授权（幂等）
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`)
SELECT 1, 2065, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `admin_role_menu` WHERE `role_id` = 1 AND `menu_id` = 2065);
