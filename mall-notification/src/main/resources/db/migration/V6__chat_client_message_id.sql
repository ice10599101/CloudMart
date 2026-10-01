-- N01：聊天消息客户端幂等——clientMessageId 唯一（同会话+同发送者），
-- 重试/网络重发不产生重复消息（同 ID 重放返回原消息）。
ALTER TABLE `messages`
    ADD COLUMN `client_message_id` VARCHAR(64) DEFAULT NULL
        COMMENT '客户端消息幂等键(N01:同会话+发送者唯一,重发不重复)' AFTER `type`,
    ADD UNIQUE KEY `uk_message_client` (`conversation_id`, `sender_id`, `client_message_id`);
