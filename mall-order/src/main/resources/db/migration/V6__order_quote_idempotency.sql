-- T03：报价作为订单唯一快照来源 + 数据库级幂等。
-- 1) orders.quote_id：一报价至多一单（可空列唯一索引允许多个 NULL）；
-- 2) orders.request_key：幂等键（DB 权威，替代 Redis 先占键——失败回滚后同键可重试，
--    不再被 30 分钟 Redis 键阻塞）；同键同参重放返回原单，异参 409；
-- 3) payload_hash：规范化请求摘要（幂等冲突判定依据）。
ALTER TABLE `orders`
    ADD COLUMN `quote_id` BIGINT UNSIGNED DEFAULT NULL
        COMMENT '报价ID(T03:一报价一单;快照金额以报价为准)' AFTER `activity_id`,
    ADD COLUMN `request_key` VARCHAR(64) NOT NULL DEFAULT ''
        COMMENT '幂等键(T03:用户域唯一;报价下单=quote-{id},秒杀=seckill-{requestId})' AFTER `quote_id`,
    ADD COLUMN `payload_hash` CHAR(64) DEFAULT NULL
        COMMENT '规范化请求摘要SHA-256(同键异参409)' AFTER `request_key`,
    ADD UNIQUE KEY `uk_orders_quote` (`quote_id`),
    ADD UNIQUE KEY `uk_orders_user_request` (`user_id`, `request_key`);
