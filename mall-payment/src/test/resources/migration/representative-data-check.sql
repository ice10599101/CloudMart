-- R28 迁移门禁·代表数据校验（mall-payment）
--
-- 执行时机：从 V10 灌代表数据后，Flyway 迁移到 head（V11）完成。
-- 本查询必须只输出一列一行：OK 表示存量数据语义保持；FAIL:... 阻断 CI。

SELECT CASE
    WHEN (SELECT COUNT(*) FROM payment_attempt
              WHERE order_id BETWEEN 9001001 AND 9001003) <> 4
        THEN 'FAIL: 代表数据行数变化（期望 4，迁移不得丢行/重行）'
    WHEN (SELECT COUNT(*) FROM payment_attempt
              WHERE order_id BETWEEN 9001001 AND 9001003
                AND (scene <> 'H5' OR scene IS NULL)) <> 0
        THEN 'FAIL: scene 存量回填应为 H5'
    WHEN (SELECT COUNT(*) FROM payment_attempt
              WHERE order_id BETWEEN 9001001 AND 9001003 AND status = 'PENDING'
                AND (active_order_id IS NULL OR active_order_id <> order_id)) <> 0
        THEN 'FAIL: PENDING 行 active_order_id 必须等于 order_id'
    WHEN (SELECT COUNT(*) FROM payment_attempt
              WHERE order_id BETWEEN 9001001 AND 9001003
                AND status IN ('SUCCESS', 'CLOSED', 'FAILED')
                AND active_order_id IS NOT NULL) <> 0
        THEN 'FAIL: 非 PENDING 行 active_order_id 必须为 NULL'
    WHEN (SELECT COUNT(*) FROM payment_attempt
              WHERE order_id IN (9001001, 9001002) AND status = 'PENDING') <> 2
        THEN 'FAIL: 两个订单的活动尝试应共存'
    WHEN (SELECT COUNT(*) FROM payment_attempt
              WHERE order_id BETWEEN 9001001 AND 9001003 AND request_key IS NOT NULL) <> 0
        THEN 'FAIL: 存量行 request_key 回填应为 NULL'
    ELSE 'OK'
END AS r28_payment_migration_assertion;
