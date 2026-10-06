-- ============================================================
-- E2E 验收数据集 seed（T27 性能实测前置）
-- 目标库：mall_order / mall_wish / mall_product（按服务独立库分文件执行）
-- 用途：为 P95≤500ms 查询/≤1s 写入的性能实测灌入可重复的数据形态，
--       数量按方案"用实际容量校准"原则给中等规模基线（可按倍数放大）。
-- 幂等：可重复执行（ON DUPLICATE KEY / 先删后插临时段）。
-- 执行方式：mysql -h <host> -P 8306 -u root -p mall_order < sql/e2e/seed-e2e.sql
-- ============================================================

-- ---------- 1. mall_product：SKU 与库存（订单/秒杀压测的前置） ----------
USE mall_product;

-- 商品 9001-9050，每商品 3 SKU
INSERT INTO products (id, category_id, brand_id, name, main_image, status, created_at, updated_at)
SELECT 9000 + t.n, 1, (SELECT MIN(id) FROM brands),
       CONCAT('压测商品-', t.n),
       'seed/p.png', 1, NOW(), NOW()
FROM (SELECT a.N + b.N * 10 + 1 AS n FROM
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
       SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) b) t
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 9000 + t.n);

INSERT INTO product_skus (id, product_id, sku_code, price, stock, attributes, status, created_at, updated_at)
SELECT 90000 + p.n * 10 + s.n, 9000 + p.n,
       CONCAT('SEED-SKU-', p.n, '-', s.n),
       10.00 + p.n, 1000, JSON_OBJECT('color', '默认'), 1, NOW(), NOW()
FROM (SELECT a.N + b.N * 10 + 1 AS n FROM
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
       SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) b) p,
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2) s
WHERE NOT EXISTS (SELECT 1 FROM product_skus WHERE id = 90000 + p.n * 10 + s.n);

-- ---------- 2. mall_order：订单主表（游标分页 + 状态过滤联合索引实测） ----------
USE mall_order;

-- 用户 1001-1050 各 100 单，状态加权分布（PENDING 10%/PAID 40%/SHIPPED 30%/COMPLETED 20%）
INSERT INTO orders (id, order_no, user_id, request_key, total_amount, pay_amount, discount_amount, status,
                    receiver_name, receiver_phone, receiver_address, created_at, updated_at)
SELECT
    8000000000000000000 + u.n * 1000 + o.n,
    CONCAT('SEED', u.n, '-', o.n),
    1000 + u.n,
    CONCAT('seed-', u.n, '-', o.n),
    100.00, 99.00, 0.00,
    ELT(1 + (o.n % 10 DIV 2), 'PENDING_PAYMENT', 'PENDING_PAYMENT', 'PAID', 'PAID',
        'PAID', 'PAID', 'SHIPPED', 'SHIPPED', 'SHIPPED', 'COMPLETED'),
    '收货人', '13800000000', '广东省深圳市南山区科技园1号', DATE_SUB(NOW(), INTERVAL o.n DAY), NOW()
FROM (SELECT a.N + b.N * 10 + 1 AS n FROM
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
       SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) b) u,
      (SELECT a.N + b.N * 10 AS n FROM
       (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
        SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
       (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
        SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b) o
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE id = 8000000000000000000 + u.n * 1000 + o.n);

-- 订单明细：每单 2 行（join 查询实测）
INSERT INTO order_items (id, order_id, product_id, sku_id, product_name, quantity, price)
SELECT
    9000000000000000000 + (t.oid % 10000000) * 10 + t.row_n,
    t.oid, 9000 + (t.oid % 1000), 90000 + (t.oid % 1000) * 3 + t.row_n, '压测商品', 1 + t.row_n, 49.50
FROM (SELECT o.id AS oid, (o.id % 2) AS row_n FROM orders o
      WHERE o.id BETWEEN 8000000000000000000 AND 800000000000060000
      ORDER BY o.id LIMIT 5000) t
WHERE NOT EXISTS (SELECT 1 FROM order_items
                  WHERE id = 9000000000000000000 + (t.oid % 10000000) * 10 + t.row_n);

-- ---------- 3. mall_wish：心愿与打卡（心愿列表/榜单/世界树实测） ----------
USE mall_wish;

-- 心愿 1001-1200，20 用户各 10 条，星光/祝福梯度分布（榜单 LIMIT + 封禁过滤路径）
INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                  audit_status, is_visible, fruit_type, light_count, bless_count,
                  deleted_at, created_at, updated_at)
SELECT
    7100000000000000000 + u.n * 100 + w.n,
    2000 + u.n,
    CONCAT('压测心愿-', u.n, '-', w.n), 'seed', 1001, 'PUBLIC',
    ELT(1 + (w.n % 10 DIV 5), 'ACTIVE', 'OVERDUE'),
    'APPROVED', 1, 'GLOW',
    (w.n * 7) % 100, (w.n * 3) % 50,
    NULL, DATE_SUB(NOW(), INTERVAL w.n DAY), NOW()
FROM (SELECT a.N + b.N * 10 + 1 AS n FROM
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2) a,
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
       SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b) u,
      (SELECT a.N + b.N * 10 + 1 AS n FROM
       (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
        SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
       (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
        SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b) w
WHERE NOT EXISTS (SELECT 1 FROM wish WHERE id = 7100000000000000000 + u.n * 100 + w.n);

-- 打卡行：每心愿 30 天（checkin 统计/日历实测）
INSERT INTO wish_checkin (id, wish_id, user_id, checkin_date, starlight_granted, created_at)
SELECT
    7200000000000000000 + w.n * 100 + d.n,
    7100000000000000000 + w.n,
    2000 + (w.n % 20) + 1,
    DATE_SUB(CURDATE(), INTERVAL d.n DAY),
    1, NOW()
FROM (SELECT a.N + b.N * 100 + 1 AS n FROM
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) a,
      (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) b) w,
      (SELECT a.N + b.N * 10 + 1 AS n FROM
       (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2) a,
       (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
        SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b) d
WHERE NOT EXISTS (SELECT 1 FROM wish_checkin
                  WHERE id = 7200000000000000000 + w.n * 100 + d.n);

-- ---------- 4. EXPLAIN 校准清单（灌数后逐条跑，确认走索引） ----------
-- 4.1 订单游标分页（user_id + status + id 联合索引）：
--   EXPLAIN SELECT id, order_no, status FROM mall_order.orders
--    WHERE user_id = 1001 AND status = 'PAID' ORDER BY id DESC LIMIT 20;
-- 4.2 心愿广场（visibility+audit_status+is_visible+id）：
--   EXPLAIN SELECT id, title, light_count FROM mall_wish.wish
--    WHERE visibility='PUBLIC' AND audit_status='APPROVED' AND is_visible=1
--      AND deleted_at IS NULL ORDER BY id DESC LIMIT 20;
-- 4.3 榜单候选（light_count 排序，需 idx）：
--   EXPLAIN SELECT id, light_count FROM mall_wish.wish
--    WHERE is_visible=1 AND light_count > 0 ORDER BY light_count DESC LIMIT 60;
-- 4.4 打卡天数（wish_id 聚合）：
--   EXPLAIN SELECT wish_id, COUNT(*) FROM mall_wish.wish_checkin
--    WHERE wish_id = 7100000000000000001 GROUP BY wish_id;
-- 预期：type=ref/range，rows 与数据量同量级；出现 ALL/全表扫描 → 补联合索引并重跑。
