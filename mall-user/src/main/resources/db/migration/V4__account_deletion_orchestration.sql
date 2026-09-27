-- V4: 全账号注销编排任务表（B20）
-- 统一由 mall-user 编排：PENDING → EXECUTING → EXECUTED/FAILED；
-- 各服务清理幂等完成后才最终成功；wish 等服务经内部端点（服务令牌）执行擦除。
CREATE TABLE IF NOT EXISTS `user_account_deletion_task` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `status`         ENUM('PENDING','EXECUTING','EXECUTED','FAILED','CANCELED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    `reason`         VARCHAR(500) DEFAULT NULL COMMENT '注销原因',
    `service_progress` JSON DEFAULT NULL COMMENT '各服务清理进度(wish=SUCCESS/FAILED)',
    `requested_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    `execute_after`  DATETIME(3) NOT NULL COMMENT '宽限期截止(30天)',
    `executed_at`    DATETIME(3) DEFAULT NULL COMMENT '执行完成时间',
    `canceled_at`    DATETIME(3) DEFAULT NULL COMMENT '取消时间',
    `version`        INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY `pk_user_account_deletion_task` (`id`),
    UNIQUE KEY `uk_deletion_task_user` (`user_id`),
    INDEX `idx_deletion_task_status` (`status`, `execute_after`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全账号注销编排任务(B20)';
