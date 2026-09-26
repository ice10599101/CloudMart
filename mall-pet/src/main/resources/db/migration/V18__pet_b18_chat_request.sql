-- V18: B18 聊天请求幂等
-- 现状：客户端超时重发会落两条用户消息并发两次亲密度。
-- 方案：pet_chat_message 增加 request_id（Idempotency-Key），同 (session, request_id)
--      重试直接返回既有回复对；定时任务/无键消息不受影响（NULL 不参与唯一约束）。

ALTER TABLE `pet_chat_message`
    ADD COLUMN `request_id` VARCHAR(80) DEFAULT NULL COMMENT '客户端请求幂等键(Idempotency-Key,同键重试返回既有回复)' AFTER `is_ai_reply`;

ALTER TABLE `pet_chat_message`
    ADD UNIQUE KEY `uk_chat_request` (`session_id`, `request_id`);
