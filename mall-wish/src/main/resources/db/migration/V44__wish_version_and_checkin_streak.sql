-- V44: 心愿乐观锁版本 + 打卡连续天数口径（B08/B09）
-- 背景 B08：updateWish 读取整个实体后 updateById，实体含互动计数与审核状态，
--      会覆盖并发点亮/审核；无 version 无并发保护。
-- 背景 B09：打卡 currentStreak 直接 +1（未判定昨日是否打卡）；mood 接收但未落库；
--      需 last_checkin_date 支撑"昨天→+1，否则→1"口径。

ALTER TABLE `wish`
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0
        COMMENT '乐观锁版本(B08:编辑条件更新,计数/审核状态不由普通编辑覆盖)' AFTER `expected_at`;

ALTER TABLE `wish_progress`
    ADD COLUMN `last_checkin_date` DATE DEFAULT NULL
        COMMENT '最近打卡日(用户业务日;连续天数判定依据,B09)' AFTER `max_streak`;
