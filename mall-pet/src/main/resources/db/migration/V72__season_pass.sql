-- =============================================
-- V72: 赛季通行证（§6 赛季通行证）
--   进行中赛季（pet_season ACTIVE）期间：每日任务领取/宝箱领取累积通行证经验，
--   达到档位即可领取奖励（宠物币；最终档含皮肤）。一期只开当前赛季，随赛季结算归档。
--   uk 防并发重复建行；claimed_tiers 服务端 CAS 追加（JSON 数组字符串）。
-- =============================================
CREATE TABLE IF NOT EXISTS pet_season_pass (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    season_id BIGINT UNSIGNED NOT NULL COMMENT '赛季 ID（pet_season.id）',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    pass_exp INT NOT NULL DEFAULT 0 COMMENT '通行证经验（任务领取累积）',
    claimed_tiers VARCHAR(200) NOT NULL DEFAULT '[]' COMMENT '已领取档位（JSON 数组，如 [1,2]）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_season_user (season_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '赛季通行证进度（§6）';
