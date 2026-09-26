-- V46: 举报 / 治理工单 / 治理决定 / 申诉（N01，任务书 §7）
-- 治理事实与内容状态同库实现（mall-wish），复用社区后台组件；不跨库改写。

CREATE TABLE IF NOT EXISTS `wish_moderation_case` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    `target_type`     VARCHAR(32)  NOT NULL COMMENT '目标类型:WISH/WISH_COMMENT/GROWTH_RECORD/FULFILLMENT/DRIFT_BOTTLE/BOTTLE_COMMENT',
    `target_id`       BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
    `target_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '目标版本(受理时快照)',
    `status`          ENUM('OPEN','IN_REVIEW','RESOLVED') NOT NULL DEFAULT 'OPEN' COMMENT '工单状态',
    `assignee_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '当前处理人',
    `version`         INT NOT NULL DEFAULT 0 COMMENT '乐观锁(CAS 处理)',
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `resolved_at`     DATETIME(3) DEFAULT NULL COMMENT '结案时间',
    `active_flag`     TINYINT GENERATED ALWAYS AS (IF(`status` IN ('OPEN','IN_REVIEW'), 1, NULL)) VIRTUAL COMMENT '活动工单标志(结案为NULL)',
    PRIMARY KEY `pk_wish_moderation_case` (`id`),
    UNIQUE KEY `uk_case_active` (`target_type`, `target_id`, `active_flag`),
    INDEX `idx_case_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一治理工单(同一内容同一版本仅一个活动case)';

CREATE TABLE IF NOT EXISTS `wish_moderation_decision` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    `case_id`         BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
    `actor_id`        BIGINT UNSIGNED NOT NULL COMMENT '处理人ID',
    `decision`        ENUM('NO_ACTION','HIDE','RESTORE') NOT NULL COMMENT '决定:无动作/隐藏/恢复',
    `reason_code`     VARCHAR(32) DEFAULT NULL COMMENT '原因码',
    `reason_text`     VARCHAR(500) DEFAULT NULL COMMENT '原因说明(不含敏感正文)',
    `before_state`    VARCHAR(40) DEFAULT NULL COMMENT '处理前状态快照',
    `after_state`     VARCHAR(40) DEFAULT NULL COMMENT '处理后状态快照',
    `target_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '目标版本(处理时快照)',
    `request_id`      VARCHAR(64) DEFAULT NULL COMMENT '请求ID(审计)',
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '追加写,禁止UPDATE历史决定',
    PRIMARY KEY `pk_wish_moderation_decision` (`id`),
    INDEX `idx_decision_case` (`case_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='治理决定(追加写审计)';

CREATE TABLE IF NOT EXISTS `wish_report` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    `reporter_id` BIGINT UNSIGNED NOT NULL COMMENT '举报人',
    `target_type` VARCHAR(32)  NOT NULL COMMENT '目标类型',
    `target_id`   BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
    `reason_code` ENUM('SPAM','ABUSE','FRAUD','PRIVACY','OTHER') NOT NULL COMMENT '举报原因',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '补充说明(OTHER必填)',
    `evidence_refs` VARCHAR(1000) DEFAULT NULL COMMENT '证据附件(JSON数组,≤3)',
    `case_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单',
    `status`      ENUM('PENDING','RESOLVED') NOT NULL DEFAULT 'PENDING' COMMENT '处理进度',
    `dedup_key`   VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '活动举报键(type:id:reason;结案置空释放唯一)',
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY `pk_wish_report` (`id`),
    UNIQUE KEY `uk_report_dedup` (`dedup_key`),
    INDEX `idx_report_reporter` (`reporter_id`, `created_at`),
    INDEX `idx_report_case` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容举报(未结举报同内容同理由合并)';

CREATE TABLE IF NOT EXISTS `wish_appeal` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    `decision_id`   BIGINT UNSIGNED NOT NULL COMMENT '申诉针对的治理决定',
    `appellant_id`  BIGINT UNSIGNED NOT NULL COMMENT '申诉人(被处理作者)',
    `statement`     VARCHAR(1000) NOT NULL COMMENT '申诉陈述(1-1000)',
    `evidence_refs` VARCHAR(1000) DEFAULT NULL COMMENT '证据附件(JSON,≤3)',
    `status`        ENUM('PENDING','ACCEPTED','REJECTED') NOT NULL DEFAULT 'PENDING',
    `reviewer_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '复核人(不得为原决定处理人)',
    `result_reason` VARCHAR(500) DEFAULT NULL COMMENT '复核结论说明',
    `version`       INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `resolved_at`   DATETIME(3) DEFAULT NULL,
    PRIMARY KEY `pk_wish_appeal` (`id`),
    UNIQUE KEY `uk_appeal_decision_appellant` (`decision_id`, `appellant_id`),
    INDEX `idx_appeal_appellant` (`appellant_id`, `created_at`),
    INDEX `idx_appeal_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='治理申诉(同决定同人一条;7日内)';
