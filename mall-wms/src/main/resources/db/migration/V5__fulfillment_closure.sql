-- WMS-01：履约闭环——运单号、发货时间、订单唯一包裹约束与异步基础设施

-- 运单号与发货时间（无运单不得 SHIPPED 的数据基础）
ALTER TABLE shipping_orders
    ADD COLUMN tracking_no VARCHAR(64) NULL COMMENT '承运商运单号（真实单号，建档必填）' AFTER carrier,
    ADD COLUMN shipped_at  DATETIME(3)  NULL COMMENT '出库时间' AFTER status;

-- 一订单一包裹（首期；拆包裹扩展时由 shipment 明细表接管，主键 id 即 shipmentId）
ALTER TABLE shipping_orders
    ADD UNIQUE KEY uk_shipping_order (order_id);

-- 异步可靠性基础设施（与 mall-order 同构；ORDER_SHIPPED 事件经 Outbox 投递）
CREATE TABLE outbox_event (
    id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id          VARCHAR(64)      NOT NULL COMMENT '全局唯一事件ID（UUID）',
    event_type        VARCHAR(128)     NOT NULL COMMENT '事件类型（如 ORDER_SHIPPED）',
    schema_version    INT              NOT NULL DEFAULT 1 COMMENT '载荷结构版本',
    aggregate_id      VARCHAR(64)      NOT NULL COMMENT '聚合根ID（订单ID）',
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'WMS事件Outbox（WMS-01）';

CREATE TABLE inbox_record (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    consumer     VARCHAR(64)     NOT NULL COMMENT '消费者标识',
    event_id     VARCHAR(64)     NOT NULL COMMENT '事件ID（幂等键）',
    status       VARCHAR(16)     NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/COMPLETED/FAILED',
    error        VARCHAR(1024)   NULL COMMENT '失败原因（脱敏）',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inbox_consumer_event (consumer, event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'WMS事件Inbox（WMS-01）';

CREATE TABLE compensation_task (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id       VARCHAR(128)    NOT NULL COMMENT '业务幂等键',
    action        VARCHAR(64)     NOT NULL COMMENT '处理动作（handler 注册键）',
    aggregate_id  VARCHAR(64)     NOT NULL COMMENT '聚合根ID',
    payload       TEXT            NULL COMMENT '载荷JSON',
    status        VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/DEAD_LETTER',
    attempts      INT             NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    next_run_at   DATETIME(3)     NULL COMMENT '下次执行时间（退避）',
    last_error    VARCHAR(1024)   NULL COMMENT '最后错误（脱敏）',
    locked_by     VARCHAR(64)     NULL COMMENT '执行者实例ID（租约）',
    locked_at     DATETIME(3)     NULL COMMENT '租约时间',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_compensation_task_id (task_id),
    KEY idx_compensation_status_run (status, next_run_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'WMS补偿任务（WMS-01）';
