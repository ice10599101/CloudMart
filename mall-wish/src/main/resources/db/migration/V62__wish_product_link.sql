-- =============================================
-- V62: 心愿关联商品闭环（§6 心愿关联商品闭环）
--   - wish.linked_product_id：许愿时关联的商城商品（详情页"去购买"）
--   - wish_fulfillment.purchase_order_id：还愿凭证——关联商品的已完成订单
--     （mall-order /internal/orders/purchase-evidence 校验通过后回填）
-- 幂等：IF NOT EXISTS 语义由 information_schema 守卫（可重复执行）。
-- =============================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish' AND COLUMN_NAME = 'linked_product_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE wish ADD COLUMN linked_product_id BIGINT NULL COMMENT ''关联商品ID（心愿关联商品闭环，详情页去购买）''',
    'SELECT ''wish.linked_product_id 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish' AND INDEX_NAME = 'idx_linked_product');
SET @ddl := IF(@idx_exists = 0,
    'CREATE INDEX idx_linked_product ON wish (linked_product_id)',
    'SELECT ''idx_linked_product 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wish_fulfillment' AND COLUMN_NAME = 'purchase_order_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE wish_fulfillment ADD COLUMN purchase_order_id BIGINT NULL COMMENT ''还愿购买凭证（关联商品的已完成订单 ID，服务端校验后回填）''',
    'SELECT ''wish_fulfillment.purchase_order_id 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
