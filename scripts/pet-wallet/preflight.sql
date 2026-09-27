-- =============================================================
-- scripts/pet-wallet/preflight.sql
-- 钱包切换只读预检（W04/§6.1）：只读 SELECT，禁止在生产直接执行任何 DML/DDL。
-- 表前缀按实际库名调整（默认 mall_pet / mall_wish）。
-- 结果仅用于切换清单（cutover-manifest.json）与人工核查，禁止写入仓库真实用户数据。
-- =============================================================

-- 1) 旧操作状态分布与金额（方向汇总）
SELECT status, direction, COUNT(*) AS n, SUM(amount) AS amount
FROM mall_pet.pet_operation GROUP BY status, direction;

-- 2) 未决旧单（切换门槛：必须收敛为 0，§6.5-3）
SELECT operation_id, user_id, biz_type, status, direction, amount, retry_count, next_retry_at
FROM mall_pet.pet_operation
WHERE status IN ('PENDING', 'UNKNOWN', 'COMPENSATING', 'PROCESSING')
ORDER BY id;

-- 3) 旧单退款完整性：COMPENSATED 但无对应退款终态流水（少退排查）
SELECT o.operation_id, o.user_id, o.amount
FROM mall_pet.pet_operation o
LEFT JOIN mall_pet.pet_operation r
  ON r.operation_id = CONCAT('REFUND:', LOWER(CONCAT(
       SUBSTR(MD5(o.operation_id), 1, 8), SUBSTR(MD5(o.operation_id), 9, 8),
       SUBSTR(MD5(o.operation_id), 17, 8), SUBSTR(MD5(o.operation_id), 25, 8),
       SUBSTR(MD5(o.operation_id), 33, 8), SUBSTR(MD5(o.operation_id), 41, 8),
       SUBSTR(MD5(o.operation_id), 49, 8), SUBSTR(MD5(o.operation_id), 57, 8))))
 AND r.direction = 'EARN'
WHERE o.status = 'COMPENSATED' AND r.operation_id IS NULL;

-- 4) 操作键长度分布（TX-05：>160 视为异常）
SELECT MAX(CHAR_LENGTH(operation_id)) AS max_key_len, COUNT(*) AS n
FROM mall_pet.pet_operation;
SELECT MAX(CHAR_LENGTH(operation_id)) AS max_key_len, COUNT(*) AS n
FROM mall_wish.wish_pet_operation;

-- 5) 重复主宠 / 多进行中活动（§6.1 在途活动清点）
SELECT user_id, COUNT(*) AS n FROM mall_pet.pet
WHERE is_active = 1 GROUP BY user_id HAVING COUNT(*) > 1;

-- 6) 配置版本重复（§6.1 配置快照检查）
SELECT config_type, config_id, version, COUNT(*) AS n
FROM mall_pet.pet_config_version
GROUP BY config_type, config_id, version HAVING COUNT(*) > 1;

-- 7) 社区余额分布快照（切换批次基线；输出脱敏汇总，勿导出明细入仓库）
SELECT
  COUNT(*) AS user_count,
  SUM(starlight_balance = 0) AS zero_balance_users,
  MIN(starlight_balance) AS min_balance,
  MAX(starlight_balance) AS max_balance,
  AVG(starlight_balance) AS avg_balance
FROM mall_wish.wish_user_stat;

-- 8) 新钱包当前状态（若已建账）：账户数/余额总和（应与账本 SUM(delta) 一致）
SELECT COUNT(*) AS accounts, COALESCE(SUM(balance), 0) AS total_balance, MAX(version) AS max_version
FROM mall_pet.pet_wallet_account;

-- 9) 账本主不变量抽查（对账 Job 的独立复核样本；全量以 PetWalletReconcileJob 为准）
SELECT a.id AS account_id, a.user_id, a.balance,
       COALESCE((SELECT SUM(l.delta) FROM mall_pet.pet_wallet_ledger l
                 WHERE l.account_id = a.id AND l.account_version <= a.version), 0) AS expected_balance
FROM mall_pet.pet_wallet_account a
HAVING balance <> expected_balance
LIMIT 100;

-- 10) Flyway 版本核对（本环境迁移是否到位：V21/V22/V23 齐全为切换前置条件）
SELECT version, description, success, installed_on
FROM mall_pet.flyway_schema_history
ORDER BY installed_rank DESC LIMIT 5;
SELECT version, description, success, installed_on
FROM mall_wish.flyway_schema_history
ORDER BY installed_rank DESC LIMIT 5;
