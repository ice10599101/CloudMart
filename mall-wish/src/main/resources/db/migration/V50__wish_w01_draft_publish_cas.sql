-- W01：草稿发布并发控制——状态机 DRAFT→PUBLISHING→PUBLISHED + 每草稿唯一发布结果。
-- published_wish_id 唯一约束：即使并发路径绕过应用层 CAS，也不允许两条草稿/两次发布指向同一心愿；
-- status 回填保证存量数据语义一致（已发布→PUBLISHED）。
ALTER TABLE `wish_draft`
    ADD COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT '状态:DRAFT/PUBLISHING/PUBLISHED(W01:发布 CAS 状态机)' AFTER `published_wish_id`;

UPDATE `wish_draft` SET `status` = IF(`published_wish_id` IS NULL, 'DRAFT', 'PUBLISHED');

ALTER TABLE `wish_draft`
    ADD UNIQUE KEY `uk_draft_published_wish` (`published_wish_id`);
