-- PET-14/T35：聊天占键租约——崩溃残留（超 60s 无回复）的重执行必须经 CAS 抢租约，
-- 多个恢复请求同时到达至多一个调用 AI（唯一回复键兜底最终一致性，但不消除重复模型调用成本）。

ALTER TABLE `pet_chat_message`
    ADD COLUMN `lease_owner` VARCHAR(64) NULL COMMENT '重执行租约持有者（执行者唯一标识）' AFTER `request_id`,
    ADD COLUMN `lease_until` DATETIME NULL COMMENT '租约到期时间(UTC)；到期可被其他执行者 CAS 抢占' AFTER `lease_owner`,
    ADD INDEX `idx_chat_message_lease` (`request_id`, `lease_until`);
