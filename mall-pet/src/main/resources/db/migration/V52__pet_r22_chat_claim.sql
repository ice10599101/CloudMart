-- V52 (R22): 聊天请求幂等按角色唯一——USER 行先行占键，PET 回复行同键不同角色不冲突
-- 原缺陷：uk(session_id, request_id) 下 saveMessage 落 USER+PET 两行同键，
-- 第二行必撞唯一键——带键聊天在真实库必失败（单测 mock 掩盖）；且重放检查只在
-- 回落完成后生效，重复在途请求会并发调用 AI。

ALTER TABLE `pet_chat_message` DROP KEY `uk_chat_request`;
ALTER TABLE `pet_chat_message`
    ADD UNIQUE KEY `uk_chat_request_role` (`session_id`, `request_id`, `role`);
