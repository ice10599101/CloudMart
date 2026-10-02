-- T09：秒杀请求事实表——requestId 在提交时生成并贯穿消息、订单与查询；
-- 购买限额以 (activity_id, product_id, user_id) 唯一键为权威事实（Redis 只预筛）；
-- 成交价/数量在提交时冻结快照，订单引用该报价，Redis 投影可由本表重建。

CREATE TABLE seckill_request (
    id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id    VARCHAR(64)      NOT NULL COMMENT '请求ID：提交时生成，贯穿MQ消息/订单request_key/结果查询',
    activity_id   BIGINT UNSIGNED  NOT NULL COMMENT '活动ID',
    product_id    BIGINT UNSIGNED  NOT NULL COMMENT '秒杀商品ID',
    sku_id        BIGINT UNSIGNED  NOT NULL COMMENT 'SKU ID（提交时快照）',
    user_id       BIGINT UNSIGNED  NOT NULL COMMENT '用户ID',
    quantity      INT UNSIGNED     NOT NULL DEFAULT 1 COMMENT '购买数量（提交时快照）',
    seckill_price DECIMAL(10,2)    NOT NULL COMMENT '成交单价（提交时冻结快照，订单以此计价）',
    status        VARCHAR(20)      NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-排队中/SUCCESS-成功/FAILED-终态失败',
    order_id      BIGINT UNSIGNED  NULL COMMENT '成功后关联的订单ID',
    fail_reason   VARCHAR(200)     NULL COMMENT '终态失败原因',
    send_attempts INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'MQ已发送/重发次数（发送未知恢复用）',
    next_retry_at DATETIME(3)      NULL COMMENT '下次恢复动作时间；NULL无需恢复',
    created_at    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_seckill_request_id (request_id),
    UNIQUE KEY uk_seckill_purchase_limit (activity_id, product_id, user_id),
    KEY idx_seckill_request_status_retry (status, next_retry_at),
    KEY idx_seckill_request_user (user_id, activity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀请求事实（T09）';

-- ASYNC-01：结果回写事件经 Outbox/Inbox 可靠传递，复用 mall-common 统一表结构
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

CREATE TABLE compensation_task (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id       VARCHAR(128)    NOT NULL COMMENT '业务幂等键（如 stock-confirm:{orderId}:{skuId}）',
    action        VARCHAR(64)     NOT NULL COMMENT '补偿动作标识',
    aggregate_id  VARCHAR(64)     NOT NULL COMMENT '聚合根ID（如订单号）',
    payload       TEXT            NULL COMMENT '任务载荷JSON',
    status        VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/SUCCEEDED/FAILED/DEAD_LETTER',
    attempts      INT             NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    next_retry_at DATETIME(3)     NULL COMMENT '下次重试时间（退避）',
    last_error    VARCHAR(1024)   NULL COMMENT '最后错误（脱敏）',
    locked_by     VARCHAR(64)     NULL COMMENT '执行者实例ID（租约）',
    locked_at     DATETIME(3)     NULL COMMENT '执行锁时间',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_compensation_task_id (task_id),
    KEY idx_compensation_status_retry (status, next_retry_at),
    KEY idx_compensation_aggregate (aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '跨服务补偿任务（ASYNC-01）';
