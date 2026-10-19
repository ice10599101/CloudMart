-- SF03/SF04 部署验收缺陷修复：发布者申请/访问申请是与软件无关的用户维度操作，
-- 审计写入 software_audit_log 时 software_id 为 NULL（SoftwarePublisherApplicationServiceImpl
-- 显式 setSoftwareId(null)），但表定义 software_id NOT NULL 无默认值 → 决策审计插入 500。
-- 修复：software_id 改为可空（软件维度操作仍必填，由应用层保证；申请类操作为 NULL）。

ALTER TABLE `software_audit_log`
    MODIFY `software_id` bigint unsigned DEFAULT NULL
    COMMENT '软件ID（软件维度操作必填；发布者/访问申请等用户维度操作为 NULL）';
