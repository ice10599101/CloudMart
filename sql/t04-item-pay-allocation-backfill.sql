-- T04 历史明细分摊回填（dry-run 后人工执行；不自动跑——orders/order_items 为分片表，
-- 跨片聚合核对须按分片逐一执行或直连单库环境）。
-- 规则（方案 §5.3）：仅当订单各项 price×quantity 之和恰等于订单实付（无优惠分摊歧义）
-- 时回填 pay_amount = price×quantity；有优惠的历史单留 NULL——售后金额按订单级上限
-- 人工核定，不猜金额。

-- ① dry-run：可精确回填的明细清单（价×量合计 = 实付）
SELECT oi.id, oi.order_id, oi.price, oi.quantity,
       oi.price * oi.quantity AS allocatable_pay_amount,
       o.pay_amount, g.gross
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
JOIN (
    SELECT order_id, SUM(price * quantity) AS gross
    FROM order_items
    GROUP BY order_id
) g ON g.order_id = o.id
WHERE oi.pay_amount IS NULL AND g.gross = o.pay_amount
ORDER BY oi.order_id, oi.id;

-- ② 不可精确分摊的历史单（有优惠）：留 NULL，进入人工规则
SELECT o.id AS order_id, o.pay_amount, g.gross,
       (o.pay_amount - g.gross) AS discount_total
FROM orders o
JOIN (
    SELECT order_id, SUM(price * quantity) AS gross
    FROM order_items
    GROUP BY order_id
) g ON g.order_id = o.id
WHERE o.pay_amount <> g.gross
ORDER BY o.id;

-- ③ 执行回填（确认 ① 清单后）：
-- UPDATE order_items oi
-- JOIN orders o ON o.id = oi.order_id
-- JOIN (
--     SELECT order_id, SUM(price * quantity) AS gross
--     FROM order_items GROUP BY order_id
-- ) g ON g.order_id = o.id
-- SET oi.pay_amount = oi.price * oi.quantity
-- WHERE oi.pay_amount IS NULL AND g.gross = o.pay_amount;
