-- =============================================
-- V78: R05 赛季通行证改造（与 V72 同域收敛）
--   1) pet_season_pass_claim：逐档领取事实（权威）——uk(season,user,tier) 替代
--      claimed_tiers JSON 作为领取裁决（并发领不同档互不覆盖、同档只交付一次）；
--      CLAIM_PENDING → SETTLED，交付失败可恢复补发。
--   2) pet_season_pass_exp_fact：经验事实按 (season,user,source,sourceBizId) 去重——
--      跨期按事实归属，重复消费同一任务/宝箱事实不重复计入。
--   3) pet_season_pass.version：进度行乐观锁（claimed_tiers JSON 降级为展示投影）。
--   4) pet_season.claim_until：领奖截止（NULL=随赛季结束）——截止前可补领已达标档位。
-- =============================================
ALTER TABLE pet_season_pass
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本（投影同步 CAS）' AFTER claimed_tiers;

CREATE TABLE IF NOT EXISTS pet_season_pass_claim (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    season_id BIGINT UNSIGNED NOT NULL COMMENT '赛季 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    tier INT NOT NULL COMMENT '档位（1..N）',
    status VARCHAR(20) NOT NULL DEFAULT 'CLAIM_PENDING' COMMENT 'CLAIM_PENDING/SETTLED',
    reward_snapshot VARCHAR(500) NULL COMMENT '奖励快照（币/皮肤码/规则版本）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取发起时间',
    settled_at DATETIME NULL COMMENT '交付完成时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_season_user_tier (season_id, user_id, tier),
    INDEX idx_pending (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '通行证逐档领取事实（R05 权威）';

CREATE TABLE IF NOT EXISTS pet_season_pass_exp_fact (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    season_id BIGINT UNSIGNED NOT NULL COMMENT '赛季 ID（事实归属赛季，补发不重复计入）',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    source VARCHAR(20) NOT NULL COMMENT '来源：QUEST/CHEST',
    source_biz_id VARCHAR(64) NOT NULL COMMENT '来源事实 ID（任务实例/宝箱领取）',
    exp INT NOT NULL COMMENT '经验值',
    business_date DATE NOT NULL COMMENT '事实业务日（Asia/Shanghai）',
    rule_version VARCHAR(16) NOT NULL DEFAULT 'V1' COMMENT '规则版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_fact (season_id, user_id, source, source_biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '通行证经验事实（R05 去重）';

ALTER TABLE pet_season
    ADD COLUMN claim_until DATETIME NULL COMMENT '领奖截止（NULL=随赛季结束）' AFTER ends_at;
