-- =============================================================================
-- V11__sf02_guide_issues_feedback_types.sql — SF02 完整资料与兼容性反馈
-- 1) software_revision 增加 installation_guide / known_issues：
--    随内容快照审核（revision 即审核单位），不允许上线后绕过审核修改正文。
-- 2) software_link_feedback 增加 release_key / platform / arch / system_version_text：
--    兼容性反馈（PLATFORM_MISMATCH/INSTALL_FAILURE）定位到具体业务版本、
--    来源、平台/架构与系统版本文本；不收集设备唯一标识。
-- =============================================================================

ALTER TABLE `software_revision`
    ADD COLUMN `installation_guide` TEXT NULL
        COMMENT '安装指南（步骤文本；随快照审核；仅通用文本，不含远程脚本）' AFTER `languages`,
    ADD COLUMN `known_issues` TEXT NULL
        COMMENT '已知问题（随快照审核）' AFTER `installation_guide`;

ALTER TABLE `software_link_feedback`
    MODIFY COLUMN `reason_code` varchar(32) NOT NULL
        COMMENT '反馈类型:LINK_INVALID/CODE_WRONG/FILE_MISMATCH/SUSPICIOUS/PLATFORM_MISMATCH/INSTALL_FAILURE/OTHER',
    ADD COLUMN `release_key` varchar(64) DEFAULT NULL
        COMMENT '兼容性反馈定位的业务版本键（LINK_INVALID 等链接类反馈为空）' AFTER `source_fingerprint`,
    ADD COLUMN `platform` varchar(16) DEFAULT NULL
        COMMENT '反馈时的平台（兼容性反馈）' AFTER `release_key`,
    ADD COLUMN `arch` varchar(16) DEFAULT NULL
        COMMENT '反馈时的架构（兼容性反馈）' AFTER `platform`,
    ADD COLUMN `system_version_text` varchar(200) DEFAULT NULL
        COMMENT '系统版本文本（用户自述，非设备指纹）' AFTER `arch`;

-- 兼容性反馈按版本定位的查询路径（后台按 releaseKey/type 筛选）
CREATE INDEX `idx_feedback_release`
    ON `software_link_feedback` (`software_id`, `reason_code`, `release_key`, `created_at`);
