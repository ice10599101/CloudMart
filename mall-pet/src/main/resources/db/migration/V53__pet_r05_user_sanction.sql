-- V53 (R05): 宠物域处罚事实表——USER_WARNED/USER_PET_BANNED 生成可查询的处罚记录
-- 原缺陷：resolve 只对 WALL_MESSAGE+CONTENT_REMOVED 执行内容下架，
-- USER_WARNED/USER_PET_BANNED 仅改举报状态并通知举报人——"已封禁/已警告"
-- 没有对应处罚事实，被处置用户不受任何实际限制。

CREATE TABLE IF NOT EXISTS `pet_user_sanction` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '被处置用户ID',
    `scope`            VARCHAR(30) NOT NULL COMMENT '处罚范围: SOCIAL_MUTE(社交写入)/PUBLIC_CONTENT_DISABLED(公开内容)',
    `status`           VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/EXPIRED/REVOKED',
    `starts_at`        DATETIME NOT NULL COMMENT '生效时间(UTC)',
    `expires_at`       DATETIME DEFAULT NULL COMMENT '到期时间(UTC; NULL=需人工解除)',
    `source_report_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源举报ID(可空:运营主动处置)',
    `action`           VARCHAR(30) NOT NULL COMMENT '触发动作(WARN/PET_BAN/...)',
    `reason`           VARCHAR(255) NOT NULL COMMENT '处罚理由(必填,审计可见)',
    `operator_id`      BIGINT UNSIGNED NOT NULL COMMENT '操作管理员(认证上下文)',
    `revoked_by`       BIGINT UNSIGNED DEFAULT NULL COMMENT '撤销管理员',
    `revoked_reason`   VARCHAR(255) DEFAULT NULL COMMENT '撤销理由(撤销必填)',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_user_sanction` (`id`),
    -- R05：source_report_id+action 唯一——同一举报的同一动作只生成一条处罚（重复处置幂等）
    UNIQUE KEY `uk_pet_user_sanction_source` (`source_report_id`, `action`),
    INDEX `idx_pet_user_sanction_user` (`user_id`, `scope`, `status`),
    INDEX `idx_pet_user_sanction_status` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物域用户处罚(封禁只限宠物模块新写入;查看/领奖不受影响)';
