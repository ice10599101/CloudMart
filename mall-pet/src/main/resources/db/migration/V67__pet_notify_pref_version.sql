-- V67 (§7.2): 通知偏好乐观版本——PUT 返回持久化值和 version，多端并发编辑防覆盖
ALTER TABLE `pet_notify_pref`
    ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT '乐观版本（PUT CAS）' AFTER `daily_greeting_enabled`;
