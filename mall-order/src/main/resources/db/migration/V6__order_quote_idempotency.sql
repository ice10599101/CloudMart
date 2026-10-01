-- T03：报价作为订单唯一快照来源 + 数据库级幂等。
-- 注意：本迁移对存量数据自愈——旧订单 request_key 为空串（NOT NULL DEFAULT ''），
-- 多行 (user_id,'') 会撞唯一键，先背填为每单唯一值再加键；
-- 列/键的存在性用 information_schema 守卫，半迁移状态（DDL 已执行但键失败）可安全重跑。
-- 1) orders.quote_id：一报价至多一单（可空列唯一索引允许多个 NULL）；
-- 2) orders.request_key：幂等键（DB 权威，替代 Redis 先占键——失败回滚后同键可重试，
--    不再被 30 分钟 Redis 键阻塞）；同键同参重放返回原单，异参 409；
-- 3) payload_hash：规范化请求摘要（幂等冲突判定依据）。

-- ---- 列：quote_id ----
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `orders` ADD COLUMN `quote_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''报价ID(T03:一报价一单;快照金额以报价为准)'' AFTER `activity_id`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'quote_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 列：request_key ----
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `orders` ADD COLUMN `request_key` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''幂等键(T03:用户域唯一;报价下单=quote-{id})'' AFTER `quote_id`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'request_key');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 列：payload_hash ----
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `orders` ADD COLUMN `payload_hash` CHAR(64) DEFAULT NULL COMMENT ''规范化请求摘要SHA-256(同键异参409)'' AFTER `request_key`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'payload_hash');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 存量背填：空 request_key → 每单唯一 legacy-{id}（在新代码语义中不可再产生空键） ----
UPDATE `orders` SET `request_key` = CONCAT('legacy-', `id`) WHERE `request_key` = '';

-- ---- 唯一键：uk_orders_quote（存在则跳过） ----
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `orders` ADD UNIQUE KEY `uk_orders_quote` (`quote_id`)',
    'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'uk_orders_quote');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 唯一键：uk_orders_user_request（存在则跳过） ----
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `orders` ADD UNIQUE KEY `uk_orders_user_request` (`user_id`, `request_key`)',
    'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'uk_orders_user_request');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
