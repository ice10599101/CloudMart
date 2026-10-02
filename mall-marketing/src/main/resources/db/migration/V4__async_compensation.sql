-- ASYNC-01 补充：补偿任务表（V3 漏建——CompensationTaskService 无条件装配需要此表；
-- V3 已在存量库执行过，禁改 checksum，新增 V4 补齐）

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
