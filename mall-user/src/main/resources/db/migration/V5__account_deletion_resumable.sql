-- V5 (T06)：账号注销改为跨域可恢复流程——完成语义 = 所有必需域步骤确认成功，
-- 不再用 EXECUTED 掩盖局部完成。
-- 状态机：PENDING(等待期) → PRECHECK → EXECUTING → COMPLETED；
--         失败为 BLOCKED（可重试，按步骤台账 next_retry_at 退避续跑）；CANCELED 可撤销。

ALTER TABLE `user_account_deletion_task`
    MODIFY COLUMN `status` ENUM('PENDING','PRECHECK','EXECUTING','COMPLETED','BLOCKED','CANCELED','EXECUTED')
        NOT NULL DEFAULT 'PENDING'
        COMMENT '任务状态:PENDING等待期/PRECHECK预检/EXECUTING执行中/COMPLETED全域完成/BLOCKED阻断可重试/CANCELED已撤销/EXECUTED旧语义(需补核查)',
    ADD COLUMN `block_reason` VARCHAR(200) DEFAULT NULL
        COMMENT '阻断原因(OPEN_ORDERS/ERASURE_DOMAIN_NOT_WIRED等,脱敏展示)' AFTER service_progress;

-- 注销步骤台账：每域每步骤独立事实，taskId 幂等、租约防多实例重入、退避重试可恢复
CREATE TABLE IF NOT EXISTS `user_account_deletion_step` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`      BIGINT UNSIGNED NOT NULL COMMENT '注销任务ID',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `domain`       VARCHAR(32)  NOT NULL COMMENT '域:USER/AUTH/WISH/ORDER/COMMUNITY/NOTIFICATION/FILE/PET',
    `step`         VARCHAR(64)  NOT NULL COMMENT '步骤:SESSION_REVOKE/OPEN_ORDER_CHECK/ERASE/ANONYMIZE',
    `status`       ENUM('PENDING','RUNNING','SUCCESS','FAILED','BLOCKED') NOT NULL DEFAULT 'PENDING'
        COMMENT '步骤状态:PENDING待执行/RUNNING执行中/SUCCESS成功/FAILED失败待重试/BLOCKED硬阻断',
    `attempts`     INT NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    `next_retry_at` DATETIME(3) DEFAULT NULL COMMENT '下次可重试时间(退避)',
    `lease_owner`  VARCHAR(64) DEFAULT NULL COMMENT '租约持有者(实例标识)',
    `lease_until`  DATETIME(3) DEFAULT NULL COMMENT '租约到期',
    `last_error`   VARCHAR(500) DEFAULT NULL COMMENT '最近错误(脱敏)',
    `completed_at` DATETIME(3) DEFAULT NULL COMMENT '完成时间',
    `created_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY `pk_user_account_deletion_step` (`id`),
    UNIQUE KEY `uk_deletion_step` (`task_id`, `domain`, `step`),
    INDEX `idx_deletion_step_status` (`status`, `next_retry_at`),
    INDEX `idx_deletion_step_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='注销步骤台账(T06,分域可恢复)';
