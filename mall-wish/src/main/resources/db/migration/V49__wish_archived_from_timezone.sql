-- V49: 归档来源状态（N03：取消归档时恢复到归档前状态）
ALTER TABLE `wish`
    ADD COLUMN `expected_timezone` VARCHAR(64) DEFAULT NULL COMMENT '预计完成时区(IANA,B09/N03)',
    ADD COLUMN `archived_from_status` VARCHAR(16) DEFAULT NULL
        COMMENT '归档前状态(N03:unarchive 恢复依据)' AFTER `reject_reason`;
