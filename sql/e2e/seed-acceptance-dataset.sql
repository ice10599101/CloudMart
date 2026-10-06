-- ============================================================
-- §6.1 最小验收数据集（全部 E 链共用的标准测试底座）
-- 幂等：可重复执行。固定 ID 段 89xx 与业务/压测数据隔离。
-- 执行：mysql -h <host> -P 8306 -u root -p < sql/e2e/seed-acceptance-dataset.sql
-- 用户：A=8901 正常 / B=8902 隐私限制 / C=8903 封禁（密码同 10001 测试号，可登录）
-- 管理员：ops_a=8908（客服角色）/ fin_a=8909（财务角色）
-- ============================================================

-- ---------- 1. 用户 A/B/C（mall_user） ----------
USE mall_user;

INSERT INTO users (id, username, password, email, nickname, gender, status, created_at, updated_at)
SELECT 8901, 'e2e-user-a', password, 'e2e-a@test.local', '验收A', 'MALE', 1, NOW(), NOW()
FROM users WHERE id = 8 AND NOT EXISTS (SELECT 1 FROM users WHERE id = 8901);
INSERT INTO users (id, username, password, email, nickname, gender, status, created_at, updated_at)
SELECT 8902, 'e2e-user-b', password, 'e2e-b@test.local', '验收B', 'FEMALE', 1, NOW(), NOW()
FROM users WHERE id = 8 AND NOT EXISTS (SELECT 1 FROM users WHERE id = 8902);
-- C 封禁：status=0（注销/封禁可切换断言用）
INSERT INTO users (id, username, password, email, nickname, gender, status, created_at, updated_at)
SELECT 8903, 'e2e-user-c', password, 'e2e-c@test.local', '验收C', 'MALE', 0, NOW(), NOW()
FROM users WHERE id = 8 AND NOT EXISTS (SELECT 1 FROM users WHERE id = 8903);

-- ---------- 2. 两名不同权限管理员（mall_admin，V17 角色基座） ----------
USE mall_admin;

INSERT INTO admin_user (id, dept_id, username, nickname, password, status, remark, created_at, updated_at)
SELECT 8908, 1, 'e2e-ops-a', '验收客服', password, 1, '§6.1：客服权限管理员', NOW(), NOW()
FROM admin_user WHERE id = 1 AND NOT EXISTS (SELECT 1 FROM admin_user WHERE id = 8908);
INSERT INTO admin_user (id, dept_id, username, nickname, password, status, remark, created_at, updated_at)
SELECT 8909, 1, 'e2e-fin-a', '验收财务', password, 1, '§6.1：财务权限管理员', NOW(), NOW()
FROM admin_user WHERE id = 1 AND NOT EXISTS (SELECT 1 FROM admin_user WHERE id = 8909);

-- 角色来自 V17（mall-admin 未跑 V17 时本段为空操作，重启后重跑本脚本自动补齐）
INSERT INTO admin_user_role (user_id, role_id)
SELECT 8908, r.id FROM admin_role r
WHERE r.role_key = 'customer_service'
  AND NOT EXISTS (SELECT 1 FROM admin_user_role WHERE user_id = 8908);
INSERT INTO admin_user_role (user_id, role_id)
SELECT 8909, r.id FROM admin_role r
WHERE r.role_key = 'finance_ops'
  AND NOT EXISTS (SELECT 1 FROM admin_user_role WHERE user_id = 8909);

-- ---------- 3. 直播间（主播 A + 观众 B/C） ----------
USE mall_live;

INSERT INTO live_rooms (id, title, description, anchor_user_id, anchor_name, status,
                        max_viewers, total_viewers, start_time, created_at, updated_at)
SELECT 8901, '§6.1验收直播间', 'E12/E06 底座', 8901, '验收A', 'LIVE',
       100, 0, NOW(), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM live_rooms WHERE id = 8901);

-- ---------- 4. 商品 S1/S2 + 库存 ----------
USE mall_product;

INSERT INTO products (id, category_id, brand_id, name, main_image, status, created_at, updated_at)
SELECT 9100 + t.n, 1, (SELECT MIN(id) FROM brands),
       CONCAT('验收商品S', t.n + 1), 'seed/s.png', 1, NOW(), NOW()
FROM (SELECT 0 AS n UNION SELECT 1) t
WHERE NOT EXISTS (SELECT 1 FROM products WHERE id = 9100 + t.n);

INSERT INTO product_skus (id, product_id, sku_code, price, stock, attributes, status, created_at, updated_at)
SELECT 91001 + tp.p * 10 + ts.s, 9100 + tp.p,
       CONCAT('E2E-SKU-', tp.p, '-', ts.s), 50.00 + tp.p * 10 + ts.s, 500,
       JSON_OBJECT('spec', '默认'), 1, NOW(), NOW()
FROM (SELECT 0 AS p UNION SELECT 1) tp,
     (SELECT 0 AS s UNION SELECT 1 UNION SELECT 2) ts
WHERE NOT EXISTS (SELECT 1 FROM product_skus WHERE id = 91001 + tp.p * 10 + ts.s);

-- ---------- 5. 订单 O1/O2/O3（mall_order） ----------
USE mall_order;

-- O1：A 买 S1-sku0，实付 100.00
INSERT INTO orders (id, order_no, user_id, request_key, total_amount, pay_amount, discount_amount,
                    status, receiver_name, receiver_phone, receiver_address, created_at, updated_at)
SELECT 8901000000000000001, 'E2E-O1', 8901, 'e2e-o1', 100.00, 100.00, 0.00,
       'PAID', '验收A', '13800000001', '验收地址A', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE id = 8901000000000000001);

INSERT INTO order_items (id, order_id, product_id, sku_id, product_name, quantity, price, pay_amount)
SELECT 8901000000000000011, 8901000000000000001, 9100, 91001, '验收商品S1', 2, 50.00, 100.00
WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE id = 8901000000000000011);

-- O2：两明细 + 分摊优惠（total 150，优惠 15 → 明细各付 50/85）
INSERT INTO orders (id, order_no, user_id, request_key, total_amount, pay_amount, discount_amount,
                    status, receiver_name, receiver_phone, receiver_address, created_at, updated_at)
SELECT 8901000000000000002, 'E2E-O2', 8901, 'e2e-o2', 150.00, 135.00, 15.00,
       'PAID', '验收A', '13800000001', '验收地址A', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE id = 8901000000000000002);

INSERT INTO order_items (id, order_id, product_id, sku_id, product_name, quantity, price, pay_amount)
SELECT 8901000000000000021, 8901000000000000002, 9100, 91001, '验收商品S1', 1, 50.00, 50.00
WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE id = 8901000000000000021);
INSERT INTO order_items (id, order_id, product_id, sku_id, product_name, quantity, price, pay_amount)
SELECT 8901000000000000022, 8901000000000000002, 9101, 91012, '验收商品S2', 1, 100.00, 85.00
WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE id = 8901000000000000022);

-- O3：零元单（全额优惠）
INSERT INTO orders (id, order_no, user_id, request_key, total_amount, pay_amount, discount_amount,
                    status, receiver_name, receiver_phone, receiver_address, created_at, updated_at)
SELECT 8901000000000000003, 'E2E-O3', 8901, 'e2e-o3', 50.00, 0.00, 50.00,
       'COMPLETED', '验收A', '13800000001', '验收地址A', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE id = 8901000000000000003);

INSERT INTO order_items (id, order_id, product_id, sku_id, product_name, quantity, price, pay_amount)
SELECT 8901000000000000031, 8901000000000000003, 9101, 91011, '验收商品S2', 1, 50.00, 0.00
WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE id = 8901000000000000031);

-- ---------- 6. 优惠券（一张未使用，后续可做领取/锁定/返还链） ----------
USE mall_coupon;

INSERT INTO coupon_templates (id, name, type, threshold_amount, discount_amount,
                              total_quantity, remaining_quantity, per_user_limit,
                              validity_type, valid_days, created_at, updated_at)
SELECT 8901, '§6.1满减券', 'FULL_REDUCTION', 100.00, 20.00, 100, 100, 1,
       'FIXED_DAYS', 30, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM coupon_templates WHERE id = 8901);

INSERT INTO user_coupons (id, user_id, template_id, status, received_at, expired_at, created_at)
SELECT 8901, 8901, 8901, 'UNUSED', NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW()
WHERE NOT EXISTS (SELECT 1 FROM user_coupons WHERE id = 8901);

-- ---------- 7. 阶梯促销 + 近到期拼团 + 秒杀 ----------
USE mall_marketing;

INSERT INTO tiered_promotions (id, name, description, start_time, end_time, status, created_at, updated_at)
SELECT 8901, '§6.1阶梯促销', '满两档减', DATE_SUB(NOW(), INTERVAL 1 DAY),
       DATE_ADD(NOW(), INTERVAL 7 DAY), 'ENABLED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tiered_promotions WHERE id = 8901);

INSERT INTO group_activities (id, name, description, product_id, sku_id, original_price, group_price,
                              target_number, max_groups, current_groups, per_user_limit, status,
                              start_time, end_time)
SELECT 8901, '§6.1近到期拼团', '2人团 1h 后到期', 9100, 91001, 100.00, 59.90,
       2, 10, 0, 1, 'ENABLED', DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 1 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM group_activities WHERE id = 8901);

USE mall_seckill;

INSERT INTO seckill_activities (id, name, description, start_time, end_time, status, created_at, updated_at)
SELECT 8901, '§6.1近到期秒杀', '30min 后到期', DATE_SUB(NOW(), INTERVAL 30 MINUTE),
       DATE_ADD(NOW(), INTERVAL 30 MINUTE), 'ONGOING', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM seckill_activities WHERE id = 8901);

INSERT INTO seckill_products (id, activity_id, sku_id, seckill_price, original_price,
                              total_stock, available_stock, per_user_limit, status)
SELECT 8901, 8901, 91001, 9.90, 50.00, 20, 20, 1, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM seckill_products WHERE id = 8901);

-- ---------- 8. 心愿域：公开/私密心愿 + 19 目标 + 跨月签到 + 搭子待审 + 近到期胶囊 ----------
USE mall_wish;

INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                  audit_status, is_visible, fruit_type, light_count, bless_count,
                  deleted_at, created_at, updated_at)
SELECT 8901000000000000001, 8901, '§6.1公开心愿', '公开底座', 1001, 'PUBLIC', 'ACTIVE',
       'APPROVED', 1, 'GLOW', 0, 0, NULL, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wish WHERE id = 8901000000000000001);

INSERT INTO wish (id, user_id, title, description, category_id, visibility, status,
                  audit_status, is_visible, fruit_type, light_count, bless_count,
                  deleted_at, created_at, updated_at)
SELECT 8901000000000000002, 8901, '§6.1私密心愿', '私密底座', 1001, 'PRIVATE', 'ACTIVE',
       'APPROVED', 1, 'GLOW', 0, 0, NULL, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wish WHERE id = 8901000000000000002);

-- 19 个目标（方案 §6.1：19 目标的计划，供 E07 步数上限 20 断言）
INSERT INTO wish_ai_goal (id, user_id, wish_id, title, description, estimated_days, priority, sort_order, version, status)
SELECT 8901000000000000000 + g.n, 8901, 8901000000000000001,
       CONCAT('§6.1目标-', g.n), '§6.1 底座目标', 7, 2, g.n, 0, 'PENDING'
FROM (SELECT a.n + b.n * 10 AS n FROM
      (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
       SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
      (SELECT 0 AS n UNION SELECT 1) b) g
WHERE g.n <= 19 AND NOT EXISTS
      (SELECT 1 FROM wish_ai_goal WHERE id = 8901000000000000000 + g.n);

-- 跨月签到：公开心愿近 40 天 35 行（跨月连续天数断言）
INSERT INTO wish_checkin (id, wish_id, user_id, checkin_date, content, starlight_granted, created_at)
SELECT 8901000000000000000 + d.n, 8901000000000000001, 8901,
       DATE_SUB(CURDATE(), INTERVAL d.n DAY), '§6.1跨月签到', 1, NOW()
FROM (SELECT a.n + b.n * 10 AS n FROM
      (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
       SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
      (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) b) d
WHERE d.n <= 39 AND d.n % 8 <> 0  -- 每 8 天缺勤 1 天 → 40 天里 35 行
  AND NOT EXISTS (SELECT 1 FROM wish_checkin
                  WHERE id = 8901000000000000000 + d.n);

-- 待审搭子申请：活动 + B 的 PENDING 申请
INSERT INTO wish_activity (id, type, title, description, condition_json, reward_json,
                           status, valid_from, valid_to, progress_counter, created_by, created_at, updated_at)
SELECT 8901, 'WISH_PARTNER', '§6.1搭子验收', '待审申请底座',
       JSON_OBJECT('type', 'SKILL_MATCH', 'requiredSkills', JSON_ARRAY('设计')),
       JSON_OBJECT('starlight', 20),
       'ACTIVE', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),
       0, 8901, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wish_activity WHERE id = 8901);

INSERT INTO wish_activity_participant (id, activity_id, user_id, wish_id, status, role,
                                       applied_at, created_at, updated_at)
SELECT 8901, 8901, 8902, 8901000000000000002, 'PENDING', 'MEMBER', NOW(), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wish_activity_participant WHERE id = 8901);

-- 近到期胶囊：A 的 SEALED 胶囊 2h 后到期（改期/到期提醒底座）
INSERT INTO time_capsule (id, user_id, title, content, media_urls, open_at, open_at_timezone,
                          reschedule_count, reschedule_limit, status, created_at)
SELECT 8901, 8901, '§6.1近到期胶囊', '底座内容', NULL,
       DATE_ADD(NOW(), INTERVAL 2 HOUR), 'Asia/Shanghai', 0, 3, 'SEALED', NOW()
WHERE NOT EXISTS (SELECT 1 FROM time_capsule WHERE id = 8901);

-- ---------- 9. 故意失败的 Outbox + 待发奖励（E11 底座） ----------
INSERT INTO wish_outbox (event_id, aggregate_type, aggregate_id, aggregate_version, event_type,
                         payload, status, attempts, next_attempt_at, created_at)
SELECT 'e2e-seed-fail-outbox-0001', 'WALLET', 8901, 1, 'StarlightGranted',
       JSON_OBJECT('userId', 8901, 'source', 'CHECKIN', 'amount', 2),
       'DEAD', 5, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM wish_outbox WHERE event_id = 'e2e-seed-fail-outbox-0001');

-- ---------- 10. 验收自检清单（执行后人工核对输出） ----------
-- SELECT username, status FROM mall_user.users WHERE id IN (8901,8902,8903);
-- SELECT username, remark FROM mall_admin.admin_user WHERE id IN (8908,8909);
-- SELECT id, status FROM mall_order.orders WHERE id LIKE '8901000000000000%';
-- SELECT COUNT(*) FROM mall_wish.wish_ai_goal WHERE wish_id = 8901000000000000001;  -- 19
-- SELECT COUNT(*) FROM mall_wish.wish_checkin WHERE user_id = 8901;                -- 35
-- SELECT status, attempts FROM mall_wish.wish_outbox WHERE event_id='e2e-seed-fail-outbox-0001';
