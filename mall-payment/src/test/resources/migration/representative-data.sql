-- R28 迁移门禁·代表存量数据（mall-payment）
--
-- 执行时机：Flyway 迁移到"上次发布版本"（最新迁移的前一版本，本模块为 V10）之后、
-- 继续迁移到 head（V11）之前灌入，模拟真实生产库的存量数据形态。
-- 本文件必须兼容 V10 的表结构（payment_attempt 此时尚无 scene/request_key/active_order_id）。
--
-- 专门覆盖 V11（R09）的高风险存量形态：
--  1. 同一订单一 PENDING 一 CLOSED —— STORED 生成列对 CLOSED 行产出 NULL，
--     历史多行不得被新唯一索引 uk_attempt_active_order 误杀；
--  2. 不同订单各一笔 PENDING —— 生成列值互不相同，迁移后应共存；
--  3. SUCCESS 历史行 —— 生成列同样为 NULL；
--  4. 全部存量行 request_key 为 NULL —— MySQL 唯一索引允许多个 NULL。
--
-- 若存量库曾存在"同订单两笔 PENDING"脏数据，V11 加 uk_attempt_active_order
-- 会在迁移期失败——这正是该门禁要提前暴露的问题，禁止通过删除/弱化约束规避。

INSERT INTO payment_attempt
    (order_id, merchant_payment_no, channel, amount, currency, status, provider_txn_no, version)
VALUES
    (9001001, 'R28-OLD-CLOSED-9001001', 'MOCK', 10.00, 'CNY', 'CLOSED',  NULL,        0),
    (9001001, 'R28-PENDING-9001001',    'MOCK', 10.00, 'CNY', 'PENDING', NULL,        0),
    (9001002, 'R28-PENDING-9001002',    'MOCK', 20.00, 'CNY', 'PENDING', NULL,        0),
    (9001003, 'R28-SUCCESS-9001003',    'MOCK', 30.00, 'CNY', 'SUCCESS', 'TXN-R28-1', 1);
