-- ---------------------------------------------
-- 连续签到里程碑领取记录（签到页「连续签到额外奖励」手动领取）
-- 里程碑：连续签到满 7/14/30 天可额外领取一次性星光+经验礼包，
-- uk_signin_milestone（user_id + milestone_days）保证每个里程碑仅可领取一次。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_signin_milestone_claim` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `milestone_days`    INT NOT NULL COMMENT '连续签到里程碑天数(7/14/30)',
    `starlight_reward`  INT NOT NULL COMMENT '本次领取发放星光数(快照)',
    `exp_reward`        INT NOT NULL COMMENT '本次领取发放经验数(快照)',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间(UTC)',
    PRIMARY KEY `pk_wish_signin_milestone` (`id`),
    UNIQUE KEY `uk_signin_milestone` (`user_id`, `milestone_days`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='连续签到里程碑领取记录';