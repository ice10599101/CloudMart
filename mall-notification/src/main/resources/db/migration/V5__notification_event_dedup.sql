-- V5: 宠物事件去重（B19）：pet-events MQ 为 at-least-once，重复投递会重复落库；
--     eventId（业务事件唯一键 TYPE:实例）唯一索引 + 消费端存在性检查，重复消息不再产生第二条通知。

ALTER TABLE `notifications`
    ADD COLUMN `event_id` VARCHAR(120) DEFAULT NULL COMMENT '业务事件唯一键(宠物outbox事件TYPE:实例,消费去重)' AFTER `biz_type`;

ALTER TABLE `notifications`
    ADD UNIQUE KEY `uk_notification_event` (`event_id`);
