-- T10：拼团人数/活动归属/资金模式明确
-- 首期模式固定为"成团后建单付款"：失败团释放预留权益（成员 EXPIRED），
-- 不凭空退款——group-expired 的 payment 退款消费者已随旧链路删除。

-- 组记录乐观锁版本（超时与最后一人加入竞争只允许一个终态）
ALTER TABLE `group_orders`
    ADD COLUMN `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本：终态迁移 CAS 竞争裁决' AFTER `status`;

-- 同一活动的限购事实独立于组（DB 权威，替代 Redis activity_users 投影）
ALTER TABLE `group_members`
    ADD UNIQUE INDEX `uk_group_members_activity_user` (`activity_id`, `user_id`);

-- ASYNC-01：成团事件经 Outbox 可靠传递（与成团 CAS 同事务），复用 mall-common 统一表结构
CREATE TABLE outbox_event (
    id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id          VARCHAR(64)      NOT NULL COMMENT '全局唯一事件ID（UUID）',
    event_type        VARCHAR(128)     NOT NULL COMMENT '事件类型（如 PAYMENT_SUCCESS）',
    schema_version    INT              NOT NULL DEFAULT 1 COMMENT '载荷结构版本',
    aggregate_id      VARCHAR(64)      NOT NULL COMMENT '聚合根ID（如订单号）',
    aggregate_version BIGINT           NOT NULL DEFAULT 0 COMMENT '聚合版本（乱序去重）',
    request_id        VARCHAR(64)      NULL COMMENT '发起请求追踪ID',
    payload           TEXT             NOT NULL COMMENT '事件载荷JSON',
    status            VARCHAR(16)      NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SENDING/SENT/FAILED/DEAD_LETTER',
    attempts          INT              NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    next_retry_at     DATETIME(3)      NULL COMMENT '下次重试时间（退避）',
    last_error        VARCHAR(1024)    NULL COMMENT '最后错误（脱敏）',
    locked_by         VARCHAR(64)      NULL COMMENT '投递者实例ID（租约）',
    locked_at         DATETIME(3)      NULL COMMENT '投递锁时间',
    sent_at           DATETIME(3)      NULL COMMENT '投递成功时间',
    created_at        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_status_retry (status, next_retry_at),
    KEY idx_outbox_aggregate (aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '交易事件Outbox（ASYNC-01）';

CREATE TABLE inbox_record (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    consumer     VARCHAR(64)     NOT NULL COMMENT '消费者标识',
    event_id     VARCHAR(64)     NOT NULL COMMENT '全局唯一事件ID',
    event_type   VARCHAR(128)    NOT NULL COMMENT '事件类型',
    aggregate_id VARCHAR(64)     NOT NULL COMMENT '聚合根ID',
    status       VARCHAR(16)     NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/PROCESSED/FAILED',
    attempts     INT             NOT NULL DEFAULT 0 COMMENT '处理尝试次数',
    last_error   VARCHAR(1024)   NULL COMMENT '最后错误（脱敏）',
    locked_at    DATETIME(3)     NULL COMMENT '处理锁时间（租约）',
    processed_at DATETIME(3)     NULL COMMENT '处理完成时间',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inbox_consumer_event (consumer, event_id),
    KEY idx_inbox_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '事件消费Inbox（ASYNC-01）';
