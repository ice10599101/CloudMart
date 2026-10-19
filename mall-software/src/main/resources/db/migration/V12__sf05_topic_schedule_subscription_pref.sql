-- =============================================================================
-- V12__sf05_topic_schedule_subscription_pref.sql — SF05 专题运营与订阅偏好
-- 1) software_topic：投放时间窗与介绍（线上只返回生效专题）。
-- 2) software_subscription：平台/渠道偏好（NULL=ALL，不改变旧"全部更新"语义）。
-- 3) software_fanout_task：冻结本次发布的业务变化判定与平台/渠道集合，
--    管理员可查看通知触发原因；纯文案/排序修改不生成投递。
-- =============================================================================

ALTER TABLE `software_topic`
    ADD COLUMN `description` TEXT NULL
        COMMENT '专题介绍（运营长文案）' AFTER `summary`,
    ADD COLUMN `scheduled_start_at` datetime(3) DEFAULT NULL
        COMMENT '定时上线时间（UTC；NULL=发布即生效）' AFTER `status`,
    ADD COLUMN `scheduled_end_at` datetime(3) DEFAULT NULL
        COMMENT '定时下线时间（UTC；NULL=不自动下线）' AFTER `scheduled_start_at`;

ALTER TABLE `software_subscription`
    ADD COLUMN `platforms` varchar(200) DEFAULT NULL
        COMMENT '订阅平台偏好（逗号分隔如 WINDOWS,MACOS；NULL=全部平台）' AFTER `user_id`,
    ADD COLUMN `channels` varchar(100) DEFAULT NULL
        COMMENT '订阅渠道偏好（逗号分隔如 STABLE,BETA；NULL=全部渠道）' AFTER `platforms`;

ALTER TABLE `software_fanout_task`
    ADD COLUMN `business_change` tinyint NOT NULL DEFAULT '1'
        COMMENT '本次发布是否含业务版本变化（新版本/新来源/指纹变化）；纯文案修改为 0 不通知'
        AFTER `publication_seq`,
    ADD COLUMN `platforms` varchar(500) DEFAULT NULL
        COMMENT '本次发布涉及的平台集合（判定依据，管理员可见）' AFTER `business_change`,
    ADD COLUMN `channels` varchar(300) DEFAULT NULL
        COMMENT '本次发布涉及的渠道集合' AFTER `platforms`;
