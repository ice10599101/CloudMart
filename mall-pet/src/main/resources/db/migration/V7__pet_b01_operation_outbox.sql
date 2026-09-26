-- V7: B01 星光交易幂等、结算记录与补偿（mall-pet 侧持久化业务操作 + outbox）
-- pet_operation：一笔不可回滚的远程星光交易对应一条操作记录；先提交 PENDING 才允许发起远程调用，
--                远程结果未知（超时/宕机）由恢复任务按原单号查询/重试，禁止换单号二次交易。
-- pet_outbox_event：业务事务内写入、提交后异步发送 MQ 的可靠事件（通知/成就/日记共用 eventId 去重）。

CREATE TABLE IF NOT EXISTS `pet_operation` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `operation_id`    VARCHAR(80) NOT NULL COMMENT '业务操作唯一键(BIZ:userId:业务实例,确定性生成,重试复用)',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`          BIGINT UNSIGNED DEFAULT NULL COMMENT '宠物ID(可空:非宠物维度操作)',
    `biz_type`        VARCHAR(40) NOT NULL COMMENT '业务类型:SHOP_BUY/EVOLVE/CAREER_PROMOTE/CLAIM_WORK/QUEST_CLAIM/BATTLE_REWARD/BOTTLE_REWARD/EVENT_CLAIM/COMPENSATION',
    `biz_ref_id`      BIGINT UNSIGNED DEFAULT NULL COMMENT '关联业务实例ID(activityId/questId/battleId/progressId)',
    `direction`       ENUM('EARN','SPEND') NOT NULL COMMENT '钱包方向:发放/扣减',
    `amount`          INT NOT NULL DEFAULT 0 COMMENT '星光金额(正整数,实际到账见 wallet_result)',
    `request_digest`  CHAR(64) DEFAULT NULL COMMENT '请求摘要SHA-256(幂等冲突判定)',
    `reward_snapshot` JSON DEFAULT NULL COMMENT '奖励/商品快照(本地奖励明细,完成前确定的不可变结果)',
    `status`          ENUM('PENDING','COMPLETED','FAILED','UNKNOWN','COMPENSATING','COMPENSATED') NOT NULL DEFAULT 'PENDING' COMMENT '状态机:PENDING待远程/COMPLETED完成/FAILED明确失败/UNKNOWN远程未知/COMPENSATING补偿中/COMPENSATED已补偿退款',
    `retry_count`     INT NOT NULL DEFAULT 0 COMMENT '恢复重试次数(退避递增)',
    `next_retry_at`   DATETIME DEFAULT NULL COMMENT '下次重试时间(退避调度)',
    `wallet_result`   JSON DEFAULT NULL COMMENT '钱包返回结果快照(actual/credited/balanceAfter/duplicate)',
    `last_error`      VARCHAR(500) DEFAULT NULL COMMENT '最近一次失败原因(结算中/明确失败)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    `completed_at`    DATETIME DEFAULT NULL COMMENT '终态时间(UTC)',
    PRIMARY KEY `pk_pet_operation` (`id`),
    UNIQUE KEY `uk_pet_operation_id` (`operation_id`),
    INDEX `idx_pet_operation_user` (`user_id`, `created_at`),
    INDEX `idx_pet_operation_recover` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物业务操作记录(远程星光交易幂等/补偿的事实来源)';

CREATE TABLE IF NOT EXISTS `pet_outbox_event` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `event_id`      VARCHAR(120) NOT NULL COMMENT '业务事件唯一键(TYPE:业务实例,确定性生成,消费者据此去重)',
    `event_type`    VARCHAR(60) NOT NULL COMMENT '事件类型(=MQ tag: WORK_COMPLETED/LEVEL_UP/BATTLE_FINISHED/...)',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '目标用户ID',
    `pet_id`        BIGINT UNSIGNED DEFAULT NULL COMMENT '关联宠物ID',
    `payload`       JSON NOT NULL COMMENT '消息体JSON(reminderType/title/content/bizId/bizType/eventId,Long一律字符串)',
    `status`        ENUM('NEW','SENT','FAILED') NOT NULL DEFAULT 'NEW' COMMENT '发送状态:待发/已发/失败待重试',
    `retry_count`   INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_at` DATETIME DEFAULT NULL COMMENT '下次重试时间(指数退避)',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_outbox_event` (`id`),
    UNIQUE KEY `uk_pet_outbox_event` (`event_id`),
    INDEX `idx_pet_outbox_send` (`status`, `next_retry_at`),
    INDEX `idx_pet_outbox_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物事务性事件发件箱(提交后可靠投递,幂等事件ID)';
