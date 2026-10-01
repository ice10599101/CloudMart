-- C02/QA24：作者发布状态与平台处置分离——
-- 平台隐藏（adminUpdatePostStatus status=2）后，作者不得经"草稿→发布"两步绕过恢复公开；
-- 只有平台再次处置（恢复发布 status=1）才清除该标记。
ALTER TABLE `posts`
    ADD COLUMN `moderation_hidden` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '平台处置隐藏标记(C02:置1后作者不可自行恢复发布,申诉/平台恢复发布时清除)' AFTER `status`;

UPDATE `posts` SET `moderation_hidden` = 1 WHERE `status` = 2;
