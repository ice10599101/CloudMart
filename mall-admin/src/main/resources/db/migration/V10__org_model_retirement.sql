-- ADMIN-01 组织模型退役（数据层）：
-- 1) 部门/岗位/字典/参数/代码生成菜单及按钮清理（含角色授权关联）；
-- 2) admin_role.data_scope、admin_user.dept_id 列退役（判权已收敛为超管 ALL/其余 SELF）；
-- 3) 组织与配置表 DROP（admin_dept/admin_post/admin_role_dept/admin_user_post/
--    admin_dict_type/admin_dict_data/admin_config——全部无外键引用）。
-- 前置：代码层 AdminDept*/AdminPost*/AdminConfig*/AdminDict* 全链已删除（编译绿）。

-- ===== 1. 菜单与角色授权清理 =====
DELETE FROM `admin_role_menu` WHERE `menu_id` IN (
  1006, 1007, 1008, 1009, 1010,              -- 部门管理及按钮
  1025, 1026, 1027, 1028, 1029,              -- 岗位管理及按钮
  1030, 1031, 1032, 1033, 1034,              -- 字典管理及按钮
  1040, 1041, 1042, 1043, 1044,              -- 参数设置及按钮
  6000, 6001                                 -- 工具目录/代码生成（mall-gen 模块同批退役）
);
DELETE FROM `admin_menu` WHERE `id` IN (
  1006, 1007, 1008, 1009, 1010,
  1025, 1026, 1027, 1028, 1029,
  1030, 1031, 1032, 1033, 1034,
  1040, 1041, 1042, 1043, 1044,
  6000, 6001
);

-- ===== 2. 列退役 =====
ALTER TABLE `admin_role` DROP COLUMN `data_scope`;
ALTER TABLE `admin_user` DROP COLUMN `dept_id`;

-- ===== 3. 表退役（全部无外键） =====
DROP TABLE IF EXISTS `admin_dept`;
DROP TABLE IF EXISTS `admin_post`;
DROP TABLE IF EXISTS `admin_role_dept`;
DROP TABLE IF EXISTS `admin_user_post`;
DROP TABLE IF EXISTS `admin_dict_type`;
DROP TABLE IF EXISTS `admin_dict_data`;
DROP TABLE IF EXISTS `admin_config`;
