-- =============================================
-- V63: 星光转赠（§6 星光转赠）：好友间转赠流水（审计与日限额口径）
--   约束在服务层：非自转、双方好友（任一关注方向）、单笔 10..100、
--   每日累计 ≤200、留言 ≤100 字；对转 spend/earn 同事务。
-- =============================================
CREATE TABLE IF NOT EXISTS starlight_transfer (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键（spend/earn 流水 refId）',
    from_user_id BIGINT UNSIGNED NOT NULL COMMENT '转出用户 ID',
    to_user_id BIGINT UNSIGNED NOT NULL COMMENT '转入用户 ID',
    amount INT NOT NULL COMMENT '转赠星光数量',
    message VARCHAR(100) NULL COMMENT '附言（可空）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_from_created (from_user_id, created_at),
    INDEX idx_to_created (to_user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '星光转赠流水（§6）';
