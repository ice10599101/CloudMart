-- V22: W01 独立宠物钱包（PET_COIN）——账本三表 + 幂等/订单/资产/领奖事实表
-- 设计基线见 .ice/宠物模块全栈审计与独立钱包改造实施方案.md §5.2：
--   * 余额/流水/账本同库同事务提交，账本数学约束由 CHECK 兜底（应用层仍强制校验）；
--   * 业务键 ascii_bin 二进制比较，禁止大小写不敏感导致不同幂等键合并（T13）；
--   * 流水不可更新/删除，错误用关联原单的冲正/退款新流水修正；
--   * pet_wallet_adjustment / reconcile / cutover 表随 W04（管理/对账/迁移）迁移发布。

CREATE TABLE IF NOT EXISTS `pet_wallet_account` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID(钱包按用户建账,多宠共享)',
    `currency`    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET_COIN' COMMENT '币种代码(第一版仅 PET_COIN)',
    `balance`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '余额(整数宠物币,禁止浮点)',
    `status`      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态:ACTIVE/FROZEN(冻结允许退款,禁止消费)',
    `version`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本(每笔成功收支+1,与账本 account_version 对齐)',
    `created_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_wallet_account` (`id`),
    UNIQUE KEY `uk_pet_wallet_user_currency` (`user_id`, `currency`),
    CONSTRAINT `ck_pet_wallet_balance` CHECK (`balance` >= 0),
    CONSTRAINT `ck_pet_wallet_status` CHECK (`status` IN ('ACTIVE','FROZEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币账户(按用户,期初0,无自动迁移历史余额)';

CREATE TABLE IF NOT EXISTS `pet_wallet_transaction` (
    `id`                       BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `operation_id`             VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作键(服务器固定格式 pw_:摘要,幂等入口;禁止客户端控制任意超长键)',
    `user_id`                  BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`                   BIGINT UNSIGNED DEFAULT NULL COMMENT '宠物ID(流水可关联,钱包不清空)',
    `currency`                 VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET_COIN' COMMENT '币种',
    `biz_type`                 VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务类型:PURCHASE/ACTIVITY_REWARD/BATTLE_REWARD/QUEST_REWARD/REFUND/ADJUSTMENT等(§5.3 业务键表)',
    `biz_key`                  VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务唯一键(§5.3 固定次序摘要,客户端请求键不进入该事实)',
    `direction`                VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '方向:EARN/REFUND 正向,SPEND 负向,ADJUSTMENT 按审批',
    `amount`                   BIGINT UNSIGNED NOT NULL COMMENT '金额(正整数;带符号变化见 ledger.delta)',
    `status`                   VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事务状态:COMMITTED/REJECTED(处理中放请求/订单状态,不留半成品)',
    `request_hash`             CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求规范摘要SHA-256(同键不同内容409判定)',
    `original_transaction_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '原交易ID(退款/冲正关联,累计退款≤原实扣)',
    `rule_version`             VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '规则/配置版本快照(结算口径)',
    `result_json`              JSON DEFAULT NULL COMMENT '不可变结果快照(实际入账/币种/物品;不存临时签名链接)',
    `error_code`               VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'REJECTED 时的领域错误码',
    `created_at`               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `completed_at`             DATETIME(6) DEFAULT NULL COMMENT '完成时间(UTC)',
    PRIMARY KEY `pk_pet_wallet_transaction` (`id`),
    UNIQUE KEY `uk_pet_wallet_operation` (`operation_id`),
    UNIQUE KEY `uk_pet_wallet_fact` (`user_id`, `biz_type`, `biz_key`),
    INDEX `idx_pet_wallet_tx_user_time` (`user_id`, `created_at`, `id`),
    INDEX `idx_pet_wallet_tx_original` (`original_transaction_id`),
    CONSTRAINT `ck_pet_wallet_tx_amount` CHECK (`amount` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币流水(一单一流水,不可变;成功收支唯一事实)';

CREATE TABLE IF NOT EXISTS `pet_wallet_ledger` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `transaction_id`   BIGINT UNSIGNED NOT NULL COMMENT '流水ID(一一对应)',
    `account_id`       BIGINT UNSIGNED NOT NULL COMMENT '账户ID',
    `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`           BIGINT UNSIGNED DEFAULT NULL COMMENT '宠物ID(可空)',
    `delta`            BIGINT NOT NULL COMMENT '带符号变动(EARN/REFUND+,SPEND-)',
    `balance_before`   BIGINT UNSIGNED NOT NULL COMMENT '变动前余额',
    `balance_after`    BIGINT UNSIGNED NOT NULL COMMENT '变动后余额',
    `account_version`  BIGINT UNSIGNED NOT NULL COMMENT '账户版本(=变动后 version)',
    `occurred_at`      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发生时间(UTC)',
    PRIMARY KEY `pk_pet_wallet_ledger` (`id`),
    UNIQUE KEY `uk_pet_wallet_ledger_tx` (`transaction_id`),
    UNIQUE KEY `uk_pet_wallet_ledger_version` (`account_id`, `account_version`),
    INDEX `idx_pet_wallet_ledger_user` (`user_id`, `id`),
    CONSTRAINT `ck_pet_wallet_ledger_math` CHECK (`balance_after` = `balance_before` + `delta`),
    CONSTRAINT `ck_pet_wallet_ledger_nonnegative` CHECK (`balance_before` >= 0 AND `balance_after` >= 0),
    CONSTRAINT `ck_pet_wallet_ledger_delta` CHECK (`delta` <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币账本(余额=期初0+SUM(delta),对账基准)';

CREATE TABLE IF NOT EXISTS `pet_request_dedup` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `endpoint_key`  VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化逻辑操作码(如 PURCHASE/CLAIM)',
    `request_key`   VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端请求键(16..128 ASCII,同键重试复用)',
    `payload_hash`  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范请求摘要SHA-256(同键不同内容409)',
    `biz_order_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '业务单ID(订单/claim)',
    `status`        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:PROCESSING/COMPLETED/FAILED(FAILED 可同键重试)',
    `response_json` JSON DEFAULT NULL COMMENT '终态响应快照(成功与业务拒绝均保存,同键返回原结果)',
    `created_at`    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_request_dedup` (`id`),
    UNIQUE KEY `uk_pet_request_dedup` (`user_id`, `endpoint_key`, `request_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='请求级幂等去重(网络重试/刷新/重启复用原键)';

CREATE TABLE IF NOT EXISTS `pet_purchase_order` (
    `id`                    BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`               BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`                BIGINT UNSIGNED DEFAULT NULL COMMENT '宠物ID(可空:账号级物品)',
    `item_type`             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '物品类型(EQUIPMENT/SKIN/SKILL/FOOD/FURNITURE等)',
    `item_code`             VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '物品编码(稳定code)',
    `quantity`              INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '数量(首版固定1)',
    `unit_price`            BIGINT UNSIGNED NOT NULL COMMENT '单价快照(服务端权威,禁止客户端传价)',
    `total_amount`          BIGINT UNSIGNED NOT NULL COMMENT '实扣总额(=unit_price*quantity)',
    `currency`              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET_COIN' COMMENT '币种',
    `wallet_domain`         VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET' COMMENT '钱包域(PET=宠物币;历史社区单在 pet_operation)',
    `config_version`        VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '商品配置版本(成立订单按快照结算,T22)',
    `item_snapshot`         JSON DEFAULT NULL COMMENT '物品快照(名称/资源key/价格)',
    `status`                VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:PROCESSING/COMPLETED/REJECTED',
    `wallet_transaction_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '扣款流水ID',
    `created_at`            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `completed_at`          DATETIME(6) DEFAULT NULL COMMENT '完成时间(UTC)',
    PRIMARY KEY `pk_pet_purchase_order` (`id`),
    INDEX `idx_pet_purchase_order_user` (`user_id`, `created_at`),
    INDEX `idx_pet_purchase_order_item` (`user_id`, `item_type`, `item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币购买订单(价格快照/实扣/资产交付来源)';

CREATE TABLE IF NOT EXISTS `pet_asset_grant` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '宠物ID(可空)',
    `source_type`  VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源类型:ORDER(购买)/CLAIM(领奖)/ONBOARDING等',
    `source_id`    BIGINT UNSIGNED NOT NULL COMMENT '来源单ID(订单ID/claimID)',
    `reward_slot`  VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '奖励槽位(同来源同槽唯一,防重复发放)',
    `item_type`    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '物品类型',
    `item_code`    VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '物品编码',
    `quantity`     INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '数量',
    `created_at`   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_asset_grant` (`id`),
    UNIQUE KEY `uk_pet_asset_grant_fact` (`source_type`, `source_id`, `user_id`, `reward_slot`),
    INDEX `idx_pet_asset_grant_user` (`user_id`, `item_type`, `item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产发放记录(哪一单发了哪个资产,不以库存存在替代)';

CREATE TABLE IF NOT EXISTS `pet_reward_claim` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`           BIGINT UNSIGNED DEFAULT NULL COMMENT '绑定宠物ID(领奖冻结归属,切主宠不改变)',
    `biz_type`         VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务类型:ACTIVITY_REWARD/COOP_REWARD/CHEST_REWARD等',
    `biz_id`           VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务实例ID(activityId/cooperationId+userId等)',
    `reward_slot`      VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MAIN' COMMENT '奖励槽位(同事实多奖励分离)',
    `reward_snapshot`  JSON DEFAULT NULL COMMENT '冻结奖励快照(物品/币/成长,随机结果只生成一次)',
    `wallet_domain`    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET' COMMENT '结算钱包域',
    `status`           VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:PROCESSING/COMPLETED/REJECTED',
    `result_json`      JSON DEFAULT NULL COMMENT '最终结果快照(重放返回同一结果,不重抽)',
    `created_at`       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `completed_at`     DATETIME(6) DEFAULT NULL COMMENT '完成时间(UTC)',
    PRIMARY KEY `pk_pet_reward_claim` (`id`),
    UNIQUE KEY `uk_pet_reward_claim` (`user_id`, `biz_type`, `biz_id`, `reward_slot`),
    INDEX `idx_pet_reward_claim_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励领取事实(同一业务事实至多领取一次,合作/活动/宝箱复用)';
