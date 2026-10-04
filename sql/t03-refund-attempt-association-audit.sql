-- T03 只读核查脚本：退款单 payment_attempt_id 异常关联与累计超额扫描（dry-run，无任何写操作）
-- 背景：修复前 RefundOrderMapper.insertRefund 使用 INSERT IGNORE 且透传调用方可空的
--       attemptId，严格 SQL 模式下 NOT NULL 列被静默写入默认 0——这些行的退款金额
--       不参与按真实 attempt 汇总的超退核算，存在重复全额退款风险。
-- 使用：只读执行，输出业务号清单人工核对；修复按"原订单 + 渠道事实"回填关联
--       （新代码在重放路径具备自愈能力），禁止直接删除退款记录。

-- ① 关联缺失/为零的退款单（修复前的 INSERT IGNORE 缺省行为所产生）
SELECT ro.id, ro.refund_no, ro.order_id, ro.refund_amount, ro.currency, ro.status,
       ro.payment_attempt_id, ro.created_at
FROM refund_order ro
WHERE ro.payment_attempt_id IS NULL OR ro.payment_attempt_id = 0
ORDER BY ro.created_at;

-- ② 关联悬空：attempt_id 在支付尝试表中不存在
SELECT ro.id, ro.refund_no, ro.order_id, ro.payment_attempt_id, ro.refund_amount, ro.status
FROM refund_order ro
LEFT JOIN payment_attempt pa ON pa.id = ro.payment_attempt_id
WHERE ro.payment_attempt_id > 0 AND pa.id IS NULL
ORDER BY ro.created_at;

-- ③ 订单错配：退款单与所关联支付尝试的订单不一致
SELECT ro.id, ro.refund_no, ro.order_id AS refund_order_id, ro.payment_attempt_id,
       pa.order_id AS attempt_order_id, ro.refund_amount, ro.status
FROM refund_order ro
JOIN payment_attempt pa ON pa.id = ro.payment_attempt_id
WHERE ro.order_id <> pa.order_id
ORDER BY ro.created_at;

-- ④ 关联到了未成功支付尝试的退款单
SELECT ro.id, ro.refund_no, ro.order_id, ro.payment_attempt_id, pa.status AS attempt_status,
       ro.refund_amount, ro.status AS refund_status
FROM refund_order ro
JOIN payment_attempt pa ON pa.id = ro.payment_attempt_id
WHERE pa.status <> 'SUCCESS'
ORDER BY ro.created_at;

-- ⑤ 按支付尝试累计超额：成功 + 在途（REQUESTED/PROCESSING/UNKNOWN/SUCCEEDED）> 实收
SELECT ro.payment_attempt_id, pa.order_id, pa.amount AS paid_amount,
       SUM(ro.refund_amount) AS active_refunded,
       pa.amount - SUM(ro.refund_amount) AS remaining,
       COUNT(*) AS refund_count
FROM refund_order ro
JOIN payment_attempt pa ON pa.id = ro.payment_attempt_id
WHERE ro.status IN ('REQUESTED', 'PROCESSING', 'UNKNOWN', 'SUCCEEDED')
GROUP BY ro.payment_attempt_id, pa.order_id, pa.amount
HAVING SUM(ro.refund_amount) > pa.amount
ORDER BY active_refunded - paid_amount DESC;

-- ⑥ 同一订单关联多个不同支付尝试的退款单（核对应绑定哪个成功尝试）
SELECT ro.order_id, COUNT(DISTINCT ro.payment_attempt_id) AS distinct_attempts,
       GROUP_CONCAT(DISTINCT ro.payment_attempt_id) AS attempt_ids,
       GROUP_CONCAT(ro.refund_no) AS refund_nos
FROM refund_order ro
WHERE ro.payment_attempt_id > 0
GROUP BY ro.order_id
HAVING COUNT(DISTINCT ro.payment_attempt_id) > 1;
