-- ---------------------------------------------
-- 每日签到（文档 2.6：POST /wish/my/checkin + GET /wish/my/checkin/calendar）
-- 与心愿打卡（wish_checkin，心愿维度）独立：本表为用户维度每日签到，
-- 签到发放星光 +5（文档 6.1，流水 source=SIGNIN）。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_daily_signin` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `signin_date`       DATE NOT NULL COMMENT '签到日期(UTC, 按日去重)',
    `starlight_granted` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '本次签到是否已发放星光(防重复发放)',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_daily_signin` (`id`),
    UNIQUE KEY `uk_signin_daily` (`user_id`, `signin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户每日签到记录';

-- 历史补偿：mall-wish 早期未将打卡计入 total_checkin_days（文档 6.5 打卡时 +1），
-- 按既有 wish_checkin 记录回填，等级判定（L2 累计打卡 ≥ 7）依赖该累计值。
-- GREATEST 只升不降：若 stat 累计值已大于去重打卡数（含补卡口径差异）则保持不变。
UPDATE `wish_user_stat` s
JOIN (
    SELECT user_id, COUNT(DISTINCT checkin_date) AS days
    FROM `wish_checkin`
    GROUP BY user_id
) c ON c.user_id = s.user_id
SET s.total_checkin_days = GREATEST(s.total_checkin_days, c.days),
    s.updated_at = NOW()
WHERE s.total_checkin_days < c.days;
