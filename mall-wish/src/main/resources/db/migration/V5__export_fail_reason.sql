-- OPS-02：导出任务失败原因——任务页展示失败/可受控重试的依据。
-- 兼容既有库与新建库（Flyway 顺序执行）。
ALTER TABLE `wish_data_export`
    ADD COLUMN `fail_reason` varchar(500) NULL COMMENT '失败原因(OPS-02)' AFTER `content_sha256`;
