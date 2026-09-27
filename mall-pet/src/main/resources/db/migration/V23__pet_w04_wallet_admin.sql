-- V23: W04 钱包管理——调账/对账/切换批次（§5.2 补充表 + §4.1 管理能力基线）
-- 调账：申请→另一管理员审批→原子入账（bizType=ADJUSTMENT,bizKey=adjustmentId）；同人不能审批。
-- 对账：固定 account_version 上界快照，余额 = 期初0 + SUM(delta)；差异入 item 告警，不自动改平。
-- 切换批次：LEGACY→PET 切换窗口的逐用户清单（期初 0，不复制社区余额）。

CREATE TABLE IF NOT EXISTS `pet_wallet_adjustment` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '目标用户ID',
    `delta`         BIGINT NOT NULL COMMENT '调账金额(带符号:正=补发,负=扣回;禁止0)',
    `reason`        VARCHAR(255) NOT NULL COMMENT '调账原因(必填,审计可见)',
    `ticket_no`     VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '关联工单号',
    `requested_by`  BIGINT UNSIGNED NOT NULL COMMENT '申请人(管理员ID,取认证上下文)',
    `approved_by`   BIGINT UNSIGNED DEFAULT NULL COMMENT '审批人(管理员ID;必须与申请人不同)',
    `status`        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING/APPROVED/REJECTED',
    `version`       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁(CAS 审批防并发双审)',
    `transaction_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '入账流水ID(审批成功后回填;重复审批返回原结果)',
    `created_at`    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '申请时间(UTC)',
    `reviewed_at`   DATETIME(6) DEFAULT NULL COMMENT '审批时间(UTC)',
    PRIMARY KEY `pk_pet_wallet_adjustment` (`id`),
    INDEX `idx_pet_wallet_adjustment_user` (`user_id`, `created_at`),
    INDEX `idx_pet_wallet_adjustment_status` (`status`, `created_at`),
    CONSTRAINT `ck_pet_wallet_adjustment_delta` CHECK (`delta` <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币调账(申请审批制;不直接改 balance)';

CREATE TABLE IF NOT EXISTS `pet_wallet_reconcile_run` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `cutoff`      DATETIME(6) NOT NULL COMMENT '对账截止时间(UTC)',
    `status`      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:RUNNING/COMPLETED/FAILED',
    `account_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '扫描账户数',
    `diff_count`  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '差异账户数(>0 触发告警)',
    `started_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '开始时间(UTC)',
    `finished_at` DATETIME(6) DEFAULT NULL COMMENT '结束时间(UTC)',
    PRIMARY KEY `pk_pet_wallet_reconcile_run` (`id`),
    INDEX `idx_pet_wallet_reconcile_run_time` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包对账批次(每日全量分片+分钟级抽查基线,§5.5)';

CREATE TABLE IF NOT EXISTS `pet_wallet_reconcile_item` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `run_id`           BIGINT UNSIGNED NOT NULL COMMENT '对账批次ID',
    `account_id`       BIGINT UNSIGNED NOT NULL COMMENT '账户ID',
    `expected_balance` BIGINT NOT NULL COMMENT '期望余额(=SUM(delta) 上界内)',
    `actual_balance`   BIGINT NOT NULL COMMENT '实际余额',
    `last_version`     BIGINT UNSIGNED NOT NULL COMMENT '对账时的账户版本上界',
    `diff`             BIGINT NOT NULL COMMENT '差异(actual-expected,<>0)',
    `status`           VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN/RESOLVED(人工处置,不自动改平)',
    `created_at`       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发现时间(UTC)',
    PRIMARY KEY `pk_pet_wallet_reconcile_item` (`id`),
    UNIQUE KEY `uk_pet_wallet_reconcile_item` (`run_id`, `account_id`),
    INDEX `idx_pet_wallet_reconcile_item_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账差异明细(禁止用当前余额覆盖流水;人工处置)';

CREATE TABLE IF NOT EXISTS `pet_wallet_cutover_batch` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `batch_no`         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批次号(manifest 标识)',
    `policy_version`   VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '切换政策版本(未领取奖励结算口径等)',
    `cutoff`           DATETIME(6) NOT NULL COMMENT '截止水位(UTC)',
    `status`           VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:DRAFT/RUNNING/COMPLETED/BLOCKED',
    `source_watermark` BIGINT UNSIGNED DEFAULT NULL COMMENT '旧单源水位(pet_operation 最大 id)',
    `manifest_hash`    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '清单校验摘要(SHA-256)',
    `counts`           JSON DEFAULT NULL COMMENT '分类统计(oldCurrency/newCurrency/数量/金额)',
    `operator`         BIGINT UNSIGNED NOT NULL COMMENT '执行人(管理员ID)',
    `created_at`       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `finished_at`      DATETIME(6) DEFAULT NULL COMMENT '完成时间(UTC)',
    PRIMARY KEY `pk_pet_wallet_cutover_batch` (`id`),
    UNIQUE KEY `uk_pet_wallet_cutover_batch` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包切换批次(§6.5 清单化切换;无脚本改余额)';

CREATE TABLE IF NOT EXISTS `pet_wallet_cutover_item` (
    `id`                      BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `batch_id`                BIGINT UNSIGNED NOT NULL COMMENT '批次ID',
    `user_id`                 BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `legacy_balance_snapshot` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '切换时社区余额快照(仅留档,不迁移)',
    `new_opening_balance`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '新钱包期初余额(恒0,§6.5-5)',
    `legacy_pending_count`    INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '旧未决单数(>0 阻断切换,§6.5-3)',
    `status`                  VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:PENDING/VERIFIED/FAILED',
    `error`                   VARCHAR(255) DEFAULT NULL COMMENT '核验失败原因',
    `created_at`              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_wallet_cutover_item` (`id`),
    UNIQUE KEY `uk_pet_wallet_cutover_item` (`batch_id`, `user_id`),
    INDEX `idx_pet_wallet_cutover_item_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='切换批次用户清单(逐用户核验,期初0;不执行 pet.balance=社区余额)';
