-- PRODUCT-01：商品问答管理——菜单"商城→商品问答"（问答处置：隐藏/恢复）
-- 挂在商品管理（business:product）目录下；权限点独立于编辑权限。
INSERT INTO `admin_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `remark`, `created_at`, `updated_at`, `deleted_at`) VALUES
(2065, '商品问答', 1030, 7, 'product-questions', 'admin/business/ProductQuestions', NULL, NULL, 1, 0, 'C', '0', '0', 'business:product:question', 'Q&A', 'PRODUCT-01 商品问答管理（隐藏/恢复处置）', NOW(), NOW(), NULL);

-- 角色授权：超管（role_id=1）
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`, `created_at`)
SELECT 1, 2065, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `admin_role_menu` WHERE `role_id` = 1 AND `menu_id` = 2065);
