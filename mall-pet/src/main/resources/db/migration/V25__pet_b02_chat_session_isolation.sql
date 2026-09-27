-- V25: B02 聊天会话按宠物隔离（BE-05，§3.4）
-- 唯一键 (user_id) → (user_id, pet_id)：多宠聊天历史分离，切宠不再串线。
-- 存量单会话回填 pet_id 为该用户当前主宠（历史消息留原会话，属"legacy shared"仅主人可见）；
-- 回填后仍无主宠可归属的行保持 pet_id=0，仅对主人可见。

ALTER TABLE `pet_chat_session`
    MODIFY COLUMN `pet_id` BIGINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '会话归属宠物ID(0=legacy shared 历史共享会话,仅主人可见)',
    DROP INDEX `uk_pet_chat_session_user`,
    ADD UNIQUE KEY `uk_pet_chat_session_user_pet` (`user_id`, `pet_id`);

UPDATE `pet_chat_session` s
LEFT JOIN `pet` p ON p.user_id = s.user_id AND p.is_active = 1
SET s.pet_id = COALESCE(p.id, 0)
WHERE s.pet_id = 0 OR s.pet_id IS NULL;
