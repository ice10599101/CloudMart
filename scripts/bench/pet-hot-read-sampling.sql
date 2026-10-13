-- R30 性能基线：宠物读热点 SQL 采样（只读）
--
-- 目的：在代表数据环境记录关键读路径的「执行计划 + 单条延迟 + SQL 条数」基线，
--      配合 scripts/load/pet-read-load.py 的 HTTP p50/p95/p99/错误率，形成端到端基线。
-- 执行：mysql -h <host> -P <port> -u <user> -p mall_pet < scripts/bench/pet-hot-read-sampling.sql
-- 注意：
--   1) 全程只读（SELECT/EXPLAIN），禁止在生产执行性能_schema 以外的写动作；
--   2) 代表数据门槛：pet_pve_run ≥ 1 万行、pet ≥ 5 万行时结果才具结论性，
--      数据量不足时先标注「冒烟」，不得当作优化前后结论（R30：不未经测量承诺 QPS）；
--   3) E 节经验聚合属于 mall_community 库，已用 USE 语句分段，执行账号需有两库读权限；
--   4) 结果连同数据量、机器规格、执行日期、对应 commit 记录到基线文档。

-- =====================================================================
-- A. 数据量门槛（行数 < 门槛：本轮仅冒烟）
-- =====================================================================
SELECT 'pet' AS table_name, COUNT(*) AS rows_count FROM pet
UNION ALL
SELECT 'pet_pve_run', COUNT(*) FROM pet_pve_run
UNION ALL
SELECT 'pet_pve_round_log', COUNT(*) FROM pet_pve_round_log
UNION ALL
SELECT 'pet_daily_quota', COUNT(*) FROM pet_daily_quota;

-- OPEN/FIGHTING 活跃副本分布（决定招募大厅列表的真实选择度）
SELECT status, COUNT(*) AS cnt
FROM pet_pve_run
GROUP BY status;

-- =====================================================================
-- B. PVE 列表（R30 N+1 修复点 PetPveServiceImpl.openRuns/mine）
--    期望：分页 1 次 + 宠物名 enrich 1 次 IN = 整页固定 2 条 SQL
--    修复前：分页 1 次 + 每行 selectById ×2（每页最多 100 条 SQL）
-- =====================================================================
-- B1. 招募大厅：status='OPEN' + 24h 窗口 + id 倒序（应用侧 LIMIT 最大 50）
EXPLAIN
SELECT id, boss_code, boss_name, boss_max_hp, boss_hp,
       initiator_user_id, initiator_pet_id, partner_user_id, partner_pet_id,
       initiator_pet_hp, partner_pet_hp, status, version, turn_no,
       next_actor_user_id, turn_deadline_at, run_expires_at, reward_status,
       created_at, finished_at
FROM pet_pve_run
WHERE status = 'OPEN'
  AND created_at > DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY id DESC
LIMIT 50;

-- B2. 我的副本：发起人 OR 队友（OR 跨两索引，关注 EXPLAIN 的 type/rows/Extra）
EXPLAIN
SELECT id, boss_code, boss_name, boss_max_hp, boss_hp,
       initiator_user_id, initiator_pet_id, partner_user_id, partner_pet_id,
       initiator_pet_hp, partner_pet_hp, status, version, turn_no,
       next_actor_user_id, turn_deadline_at, run_expires_at, reward_status,
       created_at, finished_at
FROM pet_pve_run
WHERE (initiator_user_id = 10001 OR partner_user_id = 10001)
ORDER BY id DESC
LIMIT 50;

-- =====================================================================
-- C. 宠物名 enrich（R30 修复后形态：整页去重 ID 单次主键 IN 查询）
--    期望：type=range（主键 IN），rows ≈ IN 列表去重后个数
--    修复前对照（勿在大表循环执行）：每行 2 次 SELECT ... WHERE id=?（type=const ×2N）
-- =====================================================================
EXPLAIN
SELECT id, name
FROM pet
WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

-- 实测单条延迟（多次执行取 TIMER_WAIT 分布；此处用 SQL_NO_CACHE 不适用 MySQL 9，
-- 以 performance_schema 采样为准，见 F 节）
SELECT id, name
FROM pet
WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

-- =====================================================================
-- D. 每日配额点查（权威计数行；uk 四列等值，期望 type=const/eq_ref，rows=1）
-- =====================================================================
EXPLAIN
SELECT id, used
FROM pet_daily_quota
WHERE user_id = 10001
  AND quota_type = 'PVE_REWARD'
  AND target_id = 0
  AND business_date = CURDATE();

-- =====================================================================
-- E. 经验事实兜底聚合（R21/R22：SUM 聚合下推，禁止读全量日志进内存求和）
--    该表在 mall_community 库；执行前切换库。
-- =====================================================================
USE mall_community;

-- 数据量门槛
SELECT COUNT(*) AS exp_logs_rows FROM exp_logs;

-- 期望：range（user_id + created_at 组合访问），扫描行仅限当日，不返回行集合
EXPLAIN
SELECT COALESCE(SUM(exp_change), 0)
FROM exp_logs
WHERE user_id = 10001
  AND created_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 24 HOUR);

USE mall_pet;

-- =====================================================================
-- F. 服务端 SQL 延迟/次数基线（MySQL 9 performance_schema，只读查询）
--    前置：performance_schema=ON（默认）；统计为实例级累积，采样前后各查一次做差。
--    采样方法：
--      1) 记录 T0；2) scripts/load/pet-read-load.py 压一轮（含 /api/pet/pve/open、/mine）；
--      3) 记录 T1；4) 对比 COUNT_STAR 增量与 AVG_TIMER_WAIT/P95（digest 不提供分位，
--         分位延迟以应用端 HTTP p95/p99 + 慢日志为准）。
-- =====================================================================
SELECT DIGEST_TEXT,
       COUNT_STAR,
       ROUND(AVG_TIMER_WAIT / 1e9, 2)  AS avg_ms,
       ROUND(MAX_TIMER_WAIT / 1e9, 2)  AS max_ms,
       SUM_ROWS_EXAMINED,
       SUM_ROWS_SENT,
       FIRST_SEEN,
       LAST_SEEN
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME IN ('mall_pet', 'mall_community')
  AND (DIGEST_TEXT LIKE '%`pet_pve_run`%'
       OR DIGEST_TEXT LIKE '%`pet`%'
       OR DIGEST_TEXT LIKE '%`pet_daily_quota`%'
       OR DIGEST_TEXT LIKE '%`exp_logs`%')
ORDER BY LAST_SEEN DESC
LIMIT 50;

-- =====================================================================
-- G. R30 SQL 条数基线（单页 size=50，由单测 PetPveServiceImplTest 断言保证）
-- ---------------------------------------------------------------------
-- 场景                         修复前              修复后
-- /pve/open 整页（2N 个宠名）   1 + 2N（≤101）      2（分页 1 + IN 1）
-- /pve/mine 整页               1 + 2N（≤101）      2（分页 1 + IN 1）
-- /pve/{id} 详情               3（run+2 名）       2（run 1 + IN 1）
-- 断言位置：mall-pet PetPveServiceImplTest.BatchEnrichTests
--   - openRuns：selectBatchIds 恰好 1 次、参数去重、selectById 0 次
--   - 空页：selectBatchIds 0 次
--   - detail：selectBatchIds 1 次、selectById 0 次
-- =====================================================================
