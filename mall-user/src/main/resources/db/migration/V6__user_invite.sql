-- =============================================
-- V6: 邀请裂变（N-3）：邀请码 + 绑定关系（双向奖励发放经 mall-wish 内部端点）
--   - user_invite_code：一人一码（唯一键），首查生成
--   - user_invite_relation：一人只可被绑定一次（唯一键）；禁止自邀/互绕
-- =============================================
CREATE TABLE IF NOT EXISTS user_invite_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    code VARCHAR(12) NOT NULL COMMENT '邀请码（8 位大写字符数字）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_user_id (user_id),
    UNIQUE INDEX uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '邀请码（N-3）';

CREATE TABLE IF NOT EXISTS user_invite_relation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    inviter_id BIGINT UNSIGNED NOT NULL COMMENT '邀请人用户 ID',
    invitee_id BIGINT UNSIGNED NOT NULL COMMENT '受邀人用户 ID（注册后绑定）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_invitee (invitee_id),
    INDEX idx_inviter (inviter_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '邀请绑定关系（N-3）';
