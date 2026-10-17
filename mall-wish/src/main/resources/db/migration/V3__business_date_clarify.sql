-- WISH-01：经济业务日口径澄清——签到/打卡事实的业务日固定按经济业务区
-- （Asia/Shanghai）归属，不再跟随用户上报时区；occurred_at_utc 事实即 created_at（UTC 存储）。
-- 用户时区上报仅用于展示与提醒时机；换设备/换时区/延迟消息均不改变业务日归属与每日唯一键。
ALTER TABLE `wish_daily_signin`
    MODIFY COLUMN `signin_date` date NOT NULL COMMENT '业务日(固定 Asia/Shanghai 日界, 按日去重; WISH-01)';
ALTER TABLE `wish_checkin`
    MODIFY COLUMN `checkin_date` date NOT NULL COMMENT '业务日(固定 Asia/Shanghai 日界, 按日去重; WISH-01)';
