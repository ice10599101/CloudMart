-- =============================================
-- V5: R08 评价以订单行为唯一资格锚点 + 后台下架理由
--
-- 1) order_item_id：评价锚定具体订单行（每已完成订单行至多一条评价）；
--    同订单同商品不同 SKU 行可分别评价（旧 uk(user,order,product) 会误拒，删除）。
-- 2) hide_reason：管理员下架理由（0=正常显示，下架时回填，作者可查）。
--
-- 存量行：order_item_id 置 NULL（历史评价不补锚点；MySQL 唯一键允许多个 NULL，
-- 不影响新行 uk 生效）；旧 uk_user_order_product 删除后，存量历史行的重复可能性
-- 不再约束（仅影响历史数据，新评价一律走订单行锚点）。
-- =============================================

ALTER TABLE `product_reviews`
    ADD COLUMN `order_item_id` BIGINT UNSIGNED NULL COMMENT 'R08 订单行锚点（order_items.id）' AFTER `order_id`,
    ADD COLUMN `hide_reason` VARCHAR(200) NULL COMMENT 'R08 管理员下架/驳回理由（作者可见）' AFTER `status`,
    DROP INDEX `uk_user_order_product`,
    ADD UNIQUE KEY `uk_user_order_item` (`user_id`, `order_item_id`),
    ADD INDEX `idx_order_item_id` (`order_item_id`);
