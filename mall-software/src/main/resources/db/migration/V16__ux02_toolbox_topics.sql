-- =============================================================================
-- V16__ux02_toolbox_topics.sql — UX02 场景专题升级"工具箱"
-- 1) software_topic：适用人群/要解决的问题/准备条件（工具箱三要素）。
-- 2) software_topic_item：分组（稳定 groupId，不靠数组位置关联）、
--    该软件在场景中的用途说明（选择理由）、可选替代标记。
-- 重复关联仍由既有 uk_topic_item 唯一键拒绝；受限软件仅展示公开信息。
-- =============================================================================

ALTER TABLE `software_topic`
    ADD COLUMN `audience` varchar(500) DEFAULT NULL
        COMMENT '适用人群（工具箱：这个工具箱适合谁）' AFTER `description`,
    ADD COLUMN `problem` varchar(500) DEFAULT NULL
        COMMENT '要解决的问题（这个工具箱能帮你做什么）' AFTER `audience`,
    ADD COLUMN `prerequisites` varchar(500) DEFAULT NULL
        COMMENT '准备条件（账号/系统要求/前置工具）' AFTER `problem`;

ALTER TABLE `software_topic_item`
    ADD COLUMN `group_key` varchar(64) NOT NULL DEFAULT '' COMMENT '分组稳定键（同专题内排序展示；空=未分组）' AFTER `sort_order`,
    ADD COLUMN `role_note` varchar(200) DEFAULT NULL COMMENT '该软件在此场景中的用途说明（选择理由，运营/作者填写）' AFTER `group_key`,
    ADD COLUMN `optional` tinyint(1) NOT NULL DEFAULT '0' COMMENT '可选替代项标记' AFTER `role_note`;
