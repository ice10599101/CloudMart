-- T15：邀请资格回调 outbox——签到事务内落库（PENDING），提交即送/失败退避补发，
-- 不再依赖"下次签到自然重试"（用户停签也能最终一致）。
CREATE TABLE `wish_invite_callback_outbox` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`              BIGINT       NOT NULL COMMENT '被邀请人',
    `distinct_signin_days` INT          NOT NULL COMMENT '回调快照：本人累计签到天数',
    `status`               VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUPERSEDED/SETTLED/FAILED',
    `attempts`             INT          NOT NULL DEFAULT 0,
    `last_error`           VARCHAR(500) NULL,
    `next_retry_at`        DATETIME     NULL,
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `settled_at`           DATETIME     NULL,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status_retry` (`status`, `next_retry_at`),
    KEY `idx_user` (`user_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='WISH-04/T15：邀请资格回调 outbox（持久重试；新行入队时同用户旧 PENDING 置 SUPERSEDED）';
